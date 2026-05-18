package com.apiplatform.controlplane.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class GatewayRefreshService {

  @Value("${app.gateway-url:http://localhost:3000}")
  private String gatewayUrl;

  @Value("${app.internal-token:internal-dev-token}")
  private String internalToken;

  private RestClient gatewayClient;

  @PostConstruct
  void init() {
    gatewayClient = RestClient.builder().baseUrl(gatewayUrl).build();
  }

  /** Best-effort — logs on failure so a missed refresh degrades to the 30s poll, never throws. */
  public void triggerRefresh() {
    try {
      gatewayClient
          .post()
          .uri("/_internal/refresh")
          .header("X-Internal-Token", internalToken)
          .retrieve()
          .toBodilessEntity();
      log.debug("Gateway route refresh triggered successfully");
    } catch (Exception e) {
      log.warn("Gateway refresh failed (routes will sync on next 30s poll): {}", e.getMessage());
    }
  }
}
