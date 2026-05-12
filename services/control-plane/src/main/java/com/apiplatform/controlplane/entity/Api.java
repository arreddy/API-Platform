package com.apiplatform.controlplane.entity;

import com.apiplatform.controlplane.converter.UuidStringConverter;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "apis")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Api {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private String id;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
  private String tenantId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  @Builder.Default
  private String version = "1.0.0";

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "oas_version", nullable = false)
  @Builder.Default
  private String oasVersion = "3.0";

  @Type(JsonType.class)
  @Column(name = "oas_document", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> oasDocument;

  @Column(name = "base_path")
  private String basePath;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<Map<String, Object>> servers = List.of();

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<Map<String, Object>> endpoints = List.of();

  @Type(JsonType.class)
  @Column(name = "security_schemes", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private Map<String, Object> securitySchemes = Map.of();

  @Column(columnDefinition = "text[]")
  @Builder.Default
  private String[] tags = new String[0];

  @Column(nullable = false)
  @Builder.Default
  private String status = "active";

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "created_by", columnDefinition = "uuid")
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @Column(name = "updated_at", nullable = false)
  @Builder.Default
  private OffsetDateTime updatedAt = OffsetDateTime.now();

  @PrePersist
  void prePersist() {
    if (this.id == null) this.id = UUID.randomUUID().toString();
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }
}
