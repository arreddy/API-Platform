package com.apiplatform.mockserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.control-plane-url}")
    private String controlPlaneUrl;

    @Value("${app.internal-token}")
    private String internalToken;

    @Cacheable(value = "oas-documents", key = "#proxyId")
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchOasForProxy(String proxyId) {
        try {
            // Get proxy to find api_id
            Map<String, Object> proxy = webClientBuilder.build()
                    .get()
                    .uri(controlPlaneUrl + "/api/v1/proxies/" + proxyId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (proxy == null || proxy.get("api_id") == null) return null;

            String apiId = (String) proxy.get("api_id");

            // Fetch the OAS document
            return webClientBuilder.build()
                    .get()
                    .uri(controlPlaneUrl + "/api/v1/apis/" + apiId + "/oas")
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.warn("Failed to fetch OAS for proxy {}: {}", proxyId, e.getMessage());
            return null;
        }
    }
}
