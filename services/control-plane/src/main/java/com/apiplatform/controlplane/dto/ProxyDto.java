package com.apiplatform.controlplane.dto;

import com.apiplatform.controlplane.entity.Proxy;
import com.apiplatform.controlplane.entity.ProxyVersion;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProxyDto {

    public record Summary(
            String id, String apiId, String name, String description,
            String targetUrl, String pathPrefix, int version,
            String status, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        public static Summary from(Proxy p) {
            return new Summary(p.getId(), p.getApiId(), p.getName(), p.getDescription(),
                    p.getTargetUrl(), p.getPathPrefix(), p.getVersion(),
                    p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record Full(
            String id, String tenantId, String apiId, String name, String description,
            String targetUrl, String pathPrefix, boolean stripPrefix, int version,
            Map<String, Object> policies, List<Map<String, Object>> routes,
            Map<String, String> headers, String status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        public static Full from(Proxy p) {
            return new Full(p.getId(), p.getTenantId(), p.getApiId(), p.getName(), p.getDescription(),
                    p.getTargetUrl(), p.getPathPrefix(), p.isStripPrefix(), p.getVersion(),
                    p.getPolicies(), p.getRoutes(), p.getHeaders(),
                    p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record VersionSummary(String id, int version, String changeNote, OffsetDateTime createdAt) {
        public static VersionSummary from(ProxyVersion v) {
            return new VersionSummary(v.getId(), v.getVersion(), v.getChangeNote(), v.getCreatedAt());
        }
    }

    // Request bodies
    public record CreateRequest(
            @NotBlank String name,
            String description,
            @NotBlank String targetUrl,
            @NotBlank String pathPrefix,
            Boolean stripPrefix,
            String apiId,
            Map<String, Object> policies,
            List<Map<String, Object>> routes,
            Map<String, String> headers
    ) {}

    public record UpdateRequest(
            String name, String description, String targetUrl,
            String pathPrefix, Boolean stripPrefix, String apiId,
            Map<String, Object> policies,
            List<Map<String, Object>> routes,
            Map<String, String> headers,
            String changeNote
    ) {}
}
