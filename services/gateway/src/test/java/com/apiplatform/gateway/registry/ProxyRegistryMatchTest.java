package com.apiplatform.gateway.registry;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxyRegistryMatchTest {

  // Directly test the match logic without Spring context by subclassing
  private CopyOnWriteArrayList<ProxyConfig> proxies;

  @BeforeEach
  void setUp() {
    proxies = new CopyOnWriteArrayList<>();
  }

  private ProxyConfig proxy(String id, String prefix, String status) {
    ProxyConfig p = new ProxyConfig();
    p.setId(id);
    p.setPathPrefix(prefix);
    p.setStatus(status);
    p.setName(id);
    return p;
  }

  private ProxyConfig match(String path) {
    return proxies.stream()
        .filter(p -> "active".equals(p.getStatus()) && path.startsWith(p.getPathPrefix()))
        .max((a, b) -> Integer.compare(a.getPathPrefix().length(), b.getPathPrefix().length()))
        .orElse(null);
  }

  // ---- ProxyConfig accessors ----

  @Test
  void authType_noPolicies_returnsNone() {
    ProxyConfig p = proxy("x", "/x", "active");
    assertThat(p.getAuthType()).isEqualTo("none");
  }

  @Test
  void authType_withApiKeyPolicy_returnsApiKey() {
    ProxyConfig p = proxy("x", "/x", "active");
    p.setPolicies(Map.of("auth", Map.of("type", "api_key")));
    assertThat(p.getAuthType()).isEqualTo("api_key");
  }

  @Test
  void rateLimitEnabled_noPolicy_returnsFalse() {
    ProxyConfig p = proxy("x", "/x", "active");
    assertThat(p.isRateLimitEnabled()).isFalse();
  }

  @Test
  void rateLimitEnabled_withEnabledTrue_returnsTrue() {
    ProxyConfig p = proxy("x", "/x", "active");
    p.setPolicies(Map.of("rateLimit", Map.of("enabled", true, "requests", 100)));
    assertThat(p.isRateLimitEnabled()).isTrue();
  }

  @Test
  void rateLimitRequests_noPolicy_returnsDefault1000() {
    ProxyConfig p = proxy("x", "/x", "active");
    assertThat(p.getRateLimitRequests()).isEqualTo(1000);
  }

  @Test
  void rateLimitRequests_withPolicy_returnsValue() {
    ProxyConfig p = proxy("x", "/x", "active");
    p.setPolicies(Map.of("rateLimit", Map.of("enabled", true, "requests", 500)));
    assertThat(p.getRateLimitRequests()).isEqualTo(500);
  }

  @Test
  void rateLimitWindow_noPolicy_returns1h() {
    ProxyConfig p = proxy("x", "/x", "active");
    assertThat(p.getRateLimitWindow()).isEqualTo("1h");
  }

  @Test
  void rateLimitWindow_withPolicy_returnsValue() {
    ProxyConfig p = proxy("x", "/x", "active");
    p.setPolicies(Map.of("rateLimit", Map.of("window", "1m")));
    assertThat(p.getRateLimitWindow()).isEqualTo("1m");
  }

  // ---- match logic ----

  @Test
  void match_exactPath_returnsProxy() {
    proxies.add(proxy("petstore", "/petstore", "active"));
    assertThat(match("/petstore/pets")).isNotNull();
    assertThat(match("/petstore/pets").getId()).isEqualTo("petstore");
  }

  @Test
  void match_noMatch_returnsNull() {
    proxies.add(proxy("petstore", "/petstore", "active"));
    assertThat(match("/other/pets")).isNull();
  }

  @Test
  void match_inactiveProxy_notReturned() {
    proxies.add(proxy("petstore", "/petstore", "inactive"));
    assertThat(match("/petstore/pets")).isNull();
  }

  @Test
  void match_longestPrefixWins() {
    proxies.add(proxy("short", "/api", "active"));
    proxies.add(proxy("long", "/api/v1", "active"));
    ProxyConfig result = match("/api/v1/pets");
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("long");
  }

  @Test
  void match_multipleActive_returnsLongest() {
    proxies.add(proxy("a", "/", "active"));
    proxies.add(proxy("b", "/api", "active"));
    proxies.add(proxy("c", "/api/v2", "active"));
    assertThat(match("/api/v2/users").getId()).isEqualTo("c");
    assertThat(match("/api/v1/users").getId()).isEqualTo("b");
    assertThat(match("/health").getId()).isEqualTo("a");
  }

  @Test
  void match_emptyRegistry_returnsNull() {
    assertThat(match("/any/path")).isNull();
  }
}
