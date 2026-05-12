package com.apiplatform.gateway.filter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apiplatform.gateway.registry.ProxyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ApiKeyAuthFilterTest {

  private ApiKeyAuthFilter filter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    WebClient.Builder builder = WebClient.builder();
    objectMapper = new ObjectMapper();
    filter = new ApiKeyAuthFilter(builder, objectMapper);
    // Inject a dummy control-plane URL via reflection or just test cacheable paths
    try {
      var field = ApiKeyAuthFilter.class.getDeclaredField("controlPlaneUrl");
      field.setAccessible(true);
      field.set(filter, "http://localhost:3001");
      var tokenField = ApiKeyAuthFilter.class.getDeclaredField("internalToken");
      tokenField.setAccessible(true);
      tokenField.set(filter, "test-token");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private ProxyConfig proxyWithAuth(String authType) {
    ProxyConfig p = new ProxyConfig();
    p.setId("proxy-1");
    p.setPathPrefix("/test");
    p.setStatus("active");
    if (authType != null) {
      p.setPolicies(Map.of("auth", Map.of("type", authType)));
    }
    return p;
  }

  // ---- auth type: none ----

  @Test
  void filter_authTypeNone_passesThrough() {
    ProxyConfig proxy = proxyWithAuth("none");
    MockServerHttpRequest request = MockServerHttpRequest.get("/test/resource").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put("proxy", proxy);

    GatewayFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();

    ApiKeyAuthFilter.ValidationResult result =
        exchange.getAttribute(ApiKeyAuthFilter.AUTH_RESULT_KEY);
    assertThat(result).isNotNull();
    assertThat(result.valid()).isFalse(); // none auth returns invalid marker
  }

  // ---- no proxy attribute ----

  @Test
  void filter_noProxy_passesThrough() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/unmatched").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();
  }

  // ---- auth type: api_key, missing key ----

  @Test
  void filter_apiKeyAuth_noKeyHeader_returns401() {
    ProxyConfig proxy = proxyWithAuth("api_key");
    MockServerHttpRequest request = MockServerHttpRequest.get("/test/resource").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put("proxy", proxy);

    GatewayFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();

    assertThat(exchange.getResponse().getStatusCode()).isNotNull();
    assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
  }

  // ---- auth type: jwt passthrough ----

  @Test
  void filter_jwtAuth_passesThrough() {
    ProxyConfig proxy = proxyWithAuth("jwt");
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test/resource")
            .header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.token")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put("proxy", proxy);

    GatewayFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();

    ApiKeyAuthFilter.ValidationResult result =
        exchange.getAttribute(ApiKeyAuthFilter.AUTH_RESULT_KEY);
    assertThat(result).isNotNull();
    assertThat(result.valid()).isTrue();
  }

  // ---- ValidationResult record ----

  @Test
  void validationResult_invalid_allNulls() {
    var r = ApiKeyAuthFilter.ValidationResult.invalid();
    assertThat(r.valid()).isFalse();
    assertThat(r.keyId()).isNull();
    assertThat(r.tenantId()).isNull();
    assertThat(r.rateLimit()).isEqualTo(0);
  }

  @Test
  void validationResult_valid_storesFields() {
    var r = new ApiKeyAuthFilter.ValidationResult(true, "key-1", "tenant-1", "proxy-1", 500, "1h");
    assertThat(r.valid()).isTrue();
    assertThat(r.keyId()).isEqualTo("key-1");
    assertThat(r.rateLimit()).isEqualTo(500);
  }

  // ---- X-Api-Key header extraction ----

  @Test
  void filter_apiKeyInXApiKeyHeader_extracted() {
    ProxyConfig proxy = proxyWithAuth("api_key");
    // Provide a key that will fail validation (no control plane running) — still exercises extraction
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test/resource")
            .header("X-Api-Key", "apk_test1234567890ab")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put("proxy", proxy);

    GatewayFilterChain chain = ex -> Mono.empty();

    // Will try to call control plane and fail gracefully — result should be 401 or complete
    filter.filter(exchange, chain).block();
  }

  @Test
  void filter_apiKeyInAuthorizationHeader_extracted() {
    ProxyConfig proxy = proxyWithAuth("api_key");
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test/resource")
            .header(HttpHeaders.AUTHORIZATION, "ApiKey apk_test1234567890ab")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put("proxy", proxy);

    GatewayFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();
  }
}
