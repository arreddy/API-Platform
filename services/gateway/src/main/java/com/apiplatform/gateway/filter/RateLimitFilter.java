package com.apiplatform.gateway.filter;

import com.apiplatform.gateway.registry.ProxyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GatewayFilter {

  private final ReactiveStringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  // In-memory fallback when Redis is unavailable
  private final ConcurrentHashMap<String, AtomicLong> memCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> memExpiry = new ConcurrentHashMap<>();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ProxyConfig proxy = exchange.getAttribute("proxy");
    if (proxy == null || !proxy.isRateLimitEnabled()) return chain.filter(exchange);

    ApiKeyAuthFilter.ValidationResult auth =
        exchange.getAttribute(ApiKeyAuthFilter.AUTH_RESULT_KEY);
    String identifier =
        (auth != null && auth.keyId() != null)
            ? auth.keyId()
            : Objects.requireNonNullElse(exchange.getRequest().getRemoteAddress(), "unknown")
                .toString();

    int limit =
        (auth != null && auth.rateLimit() > 0) ? auth.rateLimit() : proxy.getRateLimitRequests();
    long windowSeconds = parseWindow(proxy.getRateLimitWindow());
    long windowStart = System.currentTimeMillis() / (windowSeconds * 1000);
    String redisKey = "rl:" + proxy.getId() + ":" + identifier + ":" + windowStart;

    return incrementRedis(redisKey, windowSeconds)
        .onErrorResume(__ -> Mono.just((long) incrementMemory(redisKey, windowSeconds)))
        .flatMap(
            count -> {
              exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit));
              exchange
                  .getResponse()
                  .getHeaders()
                  .set("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));

              if (count > limit) {
                return reject(exchange);
              }
              return chain.filter(exchange);
            });
  }

  private Mono<Long> incrementRedis(String key, long windowSeconds) {
    return redisTemplate
        .opsForValue()
        .increment(key)
        .flatMap(
            count -> {
              if (count == 1) {
                return redisTemplate
                    .expire(key, Duration.ofSeconds(windowSeconds))
                    .thenReturn(count);
              }
              return Mono.just(count);
            });
  }

  private long incrementMemory(String key, long windowSeconds) {
    long now = System.currentTimeMillis();
    memExpiry.compute(
        key,
        (k, exp) -> {
          if (exp == null || exp < now) {
            memCounters.put(k, new AtomicLong(0));
            return now + windowSeconds * 1000;
          }
          return exp;
        });
    return memCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
  }

  private long parseWindow(String window) {
    if (window == null) return 3600;
    char unit = window.charAt(window.length() - 1);
    long n = Long.parseLong(window.substring(0, window.length() - 1));
    return switch (unit) {
      case 's' -> n;
      case 'm' -> n * 60;
      case 'h' -> n * 3600;
      case 'd' -> n * 86400;
      default -> 3600;
    };
  }

  private Mono<Void> reject(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      byte[] bytes = objectMapper.writeValueAsBytes(Map.of("error", "Rate limit exceeded"));
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (Exception e) {
      return exchange.getResponse().setComplete();
    }
  }
}
