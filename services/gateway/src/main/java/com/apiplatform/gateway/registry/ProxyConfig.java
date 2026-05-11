package com.apiplatform.gateway.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyConfig {

    private String id;
    private String tenantId;
    private String apiId;
    private String name;
    private String targetUrl;
    private String pathPrefix;
    private boolean stripPrefix = true;

    private int version;

    private Map<String, Object> policies;

    private List<Map<String, Object>> routes;

    private Map<String, String> headers;

    private String status;

    // Convenience accessors into the policies map
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRateLimitPolicy() {
        if (policies == null) return null;
        return (Map<String, Object>) policies.get("rateLimit");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAuthPolicy() {
        if (policies == null) return null;
        return (Map<String, Object>) policies.get("auth");
    }

    public String getAuthType() {
        Map<String, Object> auth = getAuthPolicy();
        return auth != null ? (String) auth.getOrDefault("type", "none") : "none";
    }

    public boolean isRateLimitEnabled() {
        Map<String, Object> rl = getRateLimitPolicy();
        return rl != null && Boolean.TRUE.equals(rl.get("enabled"));
    }

    public int getRateLimitRequests() {
        Map<String, Object> rl = getRateLimitPolicy();
        if (rl == null) return 1000;
        Object v = rl.get("requests");
        return v instanceof Number n ? n.intValue() : 1000;
    }

    public String getRateLimitWindow() {
        Map<String, Object> rl = getRateLimitPolicy();
        if (rl == null) return "1h";
        return (String) rl.getOrDefault("window", "1h");
    }
}
