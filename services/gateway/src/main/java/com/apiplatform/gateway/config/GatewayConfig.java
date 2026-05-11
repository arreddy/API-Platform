package com.apiplatform.gateway.config;

import com.apiplatform.gateway.filter.ApiKeyAuthFilter;
import com.apiplatform.gateway.filter.RateLimitFilter;
import com.apiplatform.gateway.registry.ProxyConfig;
import com.apiplatform.gateway.registry.ProxyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GatewayConfig {

    private final ProxyRegistry proxyRegistry;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;

    /**
     * Dynamic RouteLocator — rebuilds routes from the registry on every subscription.
     * Spring Cloud Gateway re-subscribes when RefreshRoutesEvent fires.
     */
    @Bean
    public RouteLocator dynamicRouteLocator() {
        return exchange -> {
            String path = exchange.getRequest().getURI().getPath();
            ProxyConfig proxy = proxyRegistry.match(path);

            if (proxy == null) {
                return Flux.empty(); // no match → 404 handled downstream
            }

            exchange.getAttributes().put("proxy", proxy);

            // Build the effective target URI
            String targetPath = path;
            if (proxy.isStripPrefix() && path.startsWith(proxy.getPathPrefix())) {
                targetPath = path.substring(proxy.getPathPrefix().length());
                if (targetPath.isEmpty()) targetPath = "/";
            }

            URI targetUri = URI.create(proxy.getTargetUrl() + targetPath);

            Route route = Route.async()
                    .id("dynamic-" + proxy.getId())
                    .uri(proxy.getTargetUrl())
                    .order(0)
                    .asyncPredicate(ex -> reactor.core.publisher.Mono.just(
                            ex.getRequest().getURI().getPath().startsWith(proxy.getPathPrefix())))
                    .filter((ex, chain) -> {
                        // Inject custom headers
                        ServerWebExchange mutated = ex.mutate()
                                .request(r -> {
                                    r.path(targetPath);
                                    if (proxy.getHeaders() != null) {
                                        proxy.getHeaders().forEach(r::header);
                                    }
                                    r.header("X-Forwarded-By", "api-gateway");
                                })
                                .build();
                        // Chain: auth → rate limit → forward
                        return apiKeyAuthFilter.filter(mutated, next ->
                                rateLimitFilter.filter(next, chain));
                    })
                    .build();

            return Flux.just(route);
        };
    }

    @Bean
    public RouterFunction<ServerResponse> healthRoute() {
        return RouterFunctions.route(
                RequestPredicates.GET("/health"),
                req -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("status", "ok", "service", "gateway")));
    }
}
