package com.apiplatform.gateway.filter;

import com.apiplatform.gateway.registry.ProxyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestLogFilter implements GlobalFilter, Ordered {

  private final WebClient.Builder webClientBuilder;

  @Value("${app.control-plane-url}")
  private String controlPlaneUrl;

  @Value("${app.internal-token}")
  private String internalToken;

  private final CopyOnWriteArrayList<Map<String, Object>> buffer = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  {
    scheduler.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
  }

  @Override
  public Mono<Void> filter(
      ServerWebExchange exchange,
      org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
    long start = System.currentTimeMillis();

    return chain
        .filter(exchange)
        .then(
            Mono.fromRunnable(
                () -> {
                  ProxyConfig proxy = exchange.getAttribute("proxy");
                  if (proxy == null) return;

                  ApiKeyAuthFilter.ValidationResult auth =
                      exchange.getAttribute(ApiKeyAuthFilter.AUTH_RESULT_KEY);
                  int latency = (int) (System.currentTimeMillis() - start);

                  var entry =
                      Map.<String, Object>of(
                          "tenantId",
                          proxy.getTenantId(),
                          "proxyId",
                          proxy.getId(),
                          "apiKeyId",
                          auth != null && auth.keyId() != null ? auth.keyId() : "",
                          "method",
                          exchange.getRequest().getMethod().name(),
                          "path",
                          exchange.getRequest().getPath().value(),
                          "statusCode",
                          exchange.getResponse().getStatusCode() != null
                              ? exchange.getResponse().getStatusCode().value()
                              : 0,
                          "latencyMs",
                          latency,
                          "clientIp",
                          exchange.getRequest().getRemoteAddress() != null
                              ? exchange
                                  .getRequest()
                                  .getRemoteAddress()
                                  .getAddress()
                                  .getHostAddress()
                              : "",
                          "userAgent",
                          exchange.getRequest().getHeaders().getFirst("User-Agent") != null
                              ? exchange.getRequest().getHeaders().getFirst("User-Agent")
                              : "");

                  buffer.add(entry);
                  if (buffer.size() >= 100) flush();
                }));
  }

  private void flush() {
    if (buffer.isEmpty()) return;
    List<Map<String, Object>> batch = new ArrayList<>(buffer);
    buffer.removeAll(batch);

    webClientBuilder
        .build()
        .post()
        .uri(controlPlaneUrl + "/api/v1/analytics/_internal/ingest")
        .header("X-Internal-Token", internalToken)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(batch)
        .retrieve()
        .bodyToMono(Void.class)
        .subscribe(null, err -> log.debug("Analytics ingest failed: {}", err.getMessage()));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
