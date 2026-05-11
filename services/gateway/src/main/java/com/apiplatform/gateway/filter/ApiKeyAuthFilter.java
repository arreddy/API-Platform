package com.apiplatform.gateway.filter;

import com.apiplatform.gateway.registry.ProxyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter implements GatewayFilter {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.control-plane-url}")
    private String controlPlaneUrl;

    @Value("${app.internal-token}")
    private String internalToken;

    // Cache valid keys for 60s
    private final ConcurrentHashMap<String, ValidationResult> validationCache = new ConcurrentHashMap<>();

    public record ValidationResult(boolean valid, String keyId, String tenantId,
                                   String proxyId, int rateLimit, String rateLimitWindow) {
        static ValidationResult invalid() {
            return new ValidationResult(false, null, null, null, 0, null);
        }
    }

    public static final String AUTH_RESULT_KEY = "authResult";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ProxyConfig proxy = exchange.getAttribute("proxy");
        if (proxy == null) return chain.filter(exchange);

        String authType = proxy.getAuthType();
        if ("none".equals(authType)) {
            exchange.getAttributes().put(AUTH_RESULT_KEY, ValidationResult.invalid());
            return chain.filter(exchange);
        }

        if ("api_key".equals(authType)) {
            String rawKey = extractApiKey(exchange);
            if (rawKey == null) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "API key required. Provide X-Api-Key header.");
            }
            return validateKey(rawKey, proxy.getId())
                    .flatMap(result -> {
                        if (!result.valid()) {
                            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or revoked API key");
                        }
                        exchange.getAttributes().put(AUTH_RESULT_KEY, result);
                        return chain.filter(exchange);
                    });
        }

        // JWT / OAuth2 — passthrough (backend validates)
        exchange.getAttributes().put(AUTH_RESULT_KEY, new ValidationResult(true, null, null, null, 0, null));
        return chain.filter(exchange);
    }

    private String extractApiKey(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        if (header != null) return header;
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("ApiKey ")) return auth.substring(7);
        return null;
    }

    private Mono<ValidationResult> validateKey(String rawKey, String proxyId) {
        String cacheKey = rawKey + ":" + proxyId;
        ValidationResult cached = validationCache.get(cacheKey);
        if (cached != null) return Mono.just(cached);

        return webClientBuilder.build()
                .post()
                .uri(controlPlaneUrl + "/api/v1/keys/_internal/validate")
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("key", rawKey, "proxyId", proxyId))
                .retrieve()
                .bodyToMono(ValidationResult.class)
                .onErrorReturn(ValidationResult.invalid())
                .doOnNext(r -> {
                    if (r.valid()) {
                        validationCache.put(cacheKey, r);
                        // Expire after 60s
                        Mono.delay(java.time.Duration.ofSeconds(60))
                                .subscribe(__ -> validationCache.remove(cacheKey));
                    }
                });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of("error", message));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }
}
