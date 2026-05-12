package com.apiplatform.controlplane.dto;

import com.apiplatform.controlplane.entity.ApiKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyDto {

  public record Summary(
      String id,
      String name,
      String keyPrefix,
      String proxyId,
      List<String> scopes,
      int rateLimit,
      String rateLimitWindow,
      String status,
      OffsetDateTime lastUsedAt,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {
    public static Summary from(ApiKey k) {
      return new Summary(
          k.getId(),
          k.getName(),
          k.getKeyPrefix(),
          k.getProxyId(),
          k.getScopes() != null ? Arrays.asList(k.getScopes()) : List.of(),
          k.getRateLimit(),
          k.getRateLimitWindow(),
          k.getStatus(),
          k.getLastUsedAt(),
          k.getExpiresAt(),
          k.getCreatedAt());
    }
  }

  // rawKey is only present at creation time
  public record CreateResponse(Summary key, String rawKey) {}

  public record ValidationResult(
      boolean valid,
      String keyId,
      String tenantId,
      String proxyId,
      int rateLimit,
      String rateLimitWindow) {
    public static ValidationResult invalid() {
      return new ValidationResult(false, null, null, null, 0, null);
    }
  }

  // Request bodies
  public record CreateRequest(
      @NotBlank String name,
      String proxyId,
      List<String> scopes,
      Integer rateLimit,
      String rateLimitWindow,
      String expiresAt) {}

  public record ValidateRequest(String key, String proxyId) {}
}
