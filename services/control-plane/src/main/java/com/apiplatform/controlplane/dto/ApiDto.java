package com.apiplatform.controlplane.dto;

import com.apiplatform.controlplane.entity.Api;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiDto {

  public record Summary(
      String id,
      String name,
      String title,
      String version,
      String description,
      String oasVersion,
      List<String> tags,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    public static Summary from(Api a) {
      return new Summary(
          a.getId(),
          a.getName(),
          a.getTitle(),
          a.getVersion(),
          a.getDescription(),
          a.getOasVersion(),
          a.getTags() != null ? Arrays.asList(a.getTags()) : List.of(),
          a.getStatus(),
          a.getCreatedAt(),
          a.getUpdatedAt());
    }
  }

  public record Full(
      String id,
      String name,
      String title,
      String version,
      String description,
      String oasVersion,
      Map<String, Object> oasDocument,
      String basePath,
      List<Map<String, Object>> servers,
      List<Map<String, Object>> endpoints,
      Map<String, Object> securitySchemes,
      List<String> tags,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    public static Full from(Api a) {
      return new Full(
          a.getId(),
          a.getName(),
          a.getTitle(),
          a.getVersion(),
          a.getDescription(),
          a.getOasVersion(),
          a.getOasDocument(),
          a.getBasePath(),
          a.getServers(),
          a.getEndpoints(),
          a.getSecuritySchemes(),
          a.getTags() != null ? Arrays.asList(a.getTags()) : List.of(),
          a.getStatus(),
          a.getCreatedAt(),
          a.getUpdatedAt());
    }
  }

  public record RegisterResponse(Summary api, Map<String, Object> suggested) {}

  // Request bodies
  public record UpdateRequest(String name, String description, String status, String oasContent) {}
}
