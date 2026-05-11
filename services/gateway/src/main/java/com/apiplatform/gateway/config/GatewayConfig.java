package com.apiplatform.gateway.config;

import com.apiplatform.gateway.filter.ApiKeyAuthFilter;
import com.apiplatform.gateway.filter.RateLimitFilter;
import com.apiplatform.gateway.registry.ProxyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        return () -> Flux.defer(() ->
            Flux.fromIterable(proxyRegistry.getAll())
                .map(proxy -> {
                    // Pass only scheme://host to the route URI.
                    // RouteToRequestUrlFilter (order 10000) takes the HOST from this URI
                    // and the PATH from the exchange request — so our filter just needs to
                    // rewrite the exchange path to include targetUrl's base path.
                    URI target = URI.create(proxy.getTargetUrl());
                    String hostOnlyUri = target.getScheme() + "://" + target.getHost()
                            + (target.getPort() > 0 ? ":" + target.getPort() : "");
                    String basePath = target.getRawPath() != null
                            ? target.getRawPath().replaceAll("/$", "") : "";

                    return Route.async()
                        .id("dynamic-" + proxy.getId())
                        .uri(hostOnlyUri)
                        .order(0)
                        .asyncPredicate(ex -> Mono.just(
                            ex.getRequest().getURI().getPath().startsWith(proxy.getPathPrefix())))
                        .filter(new OrderedGatewayFilter((ex, chain) -> {
                            String path = ex.getRequest().getURI().getPath();
                            String stripped = path;
                            if (proxy.isStripPrefix() && path.startsWith(proxy.getPathPrefix())) {
                                stripped = path.substring(proxy.getPathPrefix().length());
                                if (stripped.isEmpty()) stripped = "/";
                            }
                            final String fullPath = basePath + stripped;
                            ServerWebExchange mutated = ex.mutate()
                                .request(r -> {
                                    r.path(fullPath);
                                    if (proxy.getHeaders() != null) {
                                        proxy.getHeaders().forEach(r::header);
                                    }
                                    r.header("X-Forwarded-By", "api-gateway");
                                })
                                .build();
                            mutated.getAttributes().put("proxy", proxy);
                            return apiKeyAuthFilter.filter(mutated, next ->
                                rateLimitFilter.filter(next, chain));
                        }, 1))
                        .build();
                })
        );
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
