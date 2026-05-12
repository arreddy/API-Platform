package com.apiplatform.gateway.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class ProxyRegistry {

  private final WebClient webClient;
  private final String internalToken;
  private final CopyOnWriteArrayList<ProxyConfig> proxies = new CopyOnWriteArrayList<>();

  public ProxyRegistry(
      WebClient.Builder builder,
      @Value("${app.control-plane-url}") String controlPlaneUrl,
      @Value("${app.internal-token}") String internalToken) {
    this.webClient = builder.baseUrl(controlPlaneUrl).build();
    this.internalToken = internalToken;
    // Initial load
    refresh();
  }

  @Scheduled(fixedRateString = "${app.route-refresh-interval-ms:30000}")
  public void refresh() {
    webClient
        .get()
        .uri("/api/v1/proxies/_internal/active")
        .header("X-Internal-Token", internalToken)
        .retrieve()
        .bodyToMono(ProxyConfig[].class)
        .subscribe(
            configs -> {
              proxies.clear();
              if (configs != null) proxies.addAll(Arrays.asList(configs));
              log.debug("Loaded {} active proxies", proxies.size());
            },
            err -> log.warn("Failed to refresh proxies: {}", err.getMessage()));
  }

  public List<ProxyConfig> getAll() {
    return Collections.unmodifiableList(proxies);
  }

  /** Longest-prefix match */
  public ProxyConfig match(String path) {
    return proxies.stream()
        .filter(p -> "active".equals(p.getStatus()) && path.startsWith(p.getPathPrefix()))
        .max((a, b) -> Integer.compare(a.getPathPrefix().length(), b.getPathPrefix().length()))
        .orElse(null);
  }
}
