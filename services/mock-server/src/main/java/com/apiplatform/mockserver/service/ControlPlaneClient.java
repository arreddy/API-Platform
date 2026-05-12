package com.apiplatform.mockserver.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneClient {

  private final WebClient.Builder webClientBuilder;

  @Value("${app.control-plane-url}")
  private String controlPlaneUrl;

  @Value("${app.internal-token}")
  private String internalToken;

  @Cacheable(value = "oas-documents", key = "#proxyId", unless = "#result == null")
  @SuppressWarnings("unchecked")
  public Map<String, Object> fetchOasForProxy(String proxyId) {
    try {
      log.debug("Fetching proxy {} from control plane", proxyId);
      Map<String, Object> proxy =
          webClientBuilder
              .build()
              .get()
              .uri(controlPlaneUrl + "/api/v1/proxies/" + proxyId)
              .retrieve()
              .bodyToMono(Map.class)
              .block();

      log.debug("Proxy response: {}", proxy);
      if (proxy == null || proxy.get("apiId") == null) {
        log.warn("Proxy {} has no apiId linked", proxyId);
        return null;
      }

      String apiId = (String) proxy.get("apiId");
      log.debug("Fetching OAS for apiId {}", apiId);

      Map<String, Object> oas =
          webClientBuilder
              .build()
              .get()
              .uri(controlPlaneUrl + "/api/v1/apis/" + apiId + "/oas")
              .retrieve()
              .bodyToMono(Map.class)
              .block();

      log.debug("OAS fetched: {} keys", oas != null ? oas.size() : 0);
      return oas;
    } catch (Exception e) {
      log.warn("Failed to fetch OAS for proxy {}: {}", proxyId, e.getMessage());
      return null;
    }
  }
}
