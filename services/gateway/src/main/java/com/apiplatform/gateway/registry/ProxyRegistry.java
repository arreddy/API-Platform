package com.apiplatform.gateway.registry;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class ProxyRegistry {

  private final WebClient webClient;
  private final String internalToken;
  private final ApplicationEventPublisher eventPublisher;
  private final CopyOnWriteArrayList<ProxyConfig> proxies = new CopyOnWriteArrayList<>();

  public ProxyRegistry(
      WebClient.Builder builder,
      @Value("${app.control-plane-url}") String controlPlaneUrl,
      @Value("${app.internal-token}") String internalToken,
      ApplicationEventPublisher eventPublisher) {
    this.webClient = builder.baseUrl(controlPlaneUrl).build();
    this.internalToken = internalToken;
    this.eventPublisher = eventPublisher;
    // Blocking initial load so routes are available when the gateway starts
    loadBlocking();
  }

  /** Synchronous load used once at startup so routes are ready before the first request. */
  private void loadBlocking() {
    try {
      ProxyConfig[] configs =
          webClient
              .get()
              .uri("/api/v1/proxies/_internal/active")
              .header("X-Internal-Token", internalToken)
              .retrieve()
              .bodyToMono(ProxyConfig[].class)
              .block(Duration.ofSeconds(10));
      proxies.clear();
      if (configs != null) proxies.addAll(Arrays.asList(configs));
      log.info("Loaded {} active proxies at startup", proxies.size());
    } catch (Exception e) {
      log.warn("Initial proxy load failed (will retry on schedule): {}", e.getMessage());
    }
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
              log.debug("Refreshed {} active proxies", proxies.size());
              // Tell Spring Cloud Gateway to re-evaluate its RouteLocator
              eventPublisher.publishEvent(new RefreshRoutesEvent(this));
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
