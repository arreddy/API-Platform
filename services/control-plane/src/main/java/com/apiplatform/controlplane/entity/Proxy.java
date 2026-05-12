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
@Table(name = "proxies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proxy {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private String id;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
  private String tenantId;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "api_id", columnDefinition = "uuid")
  private String apiId;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "target_url", nullable = false)
  private String targetUrl;

  @Column(name = "path_prefix", nullable = false)
  private String pathPrefix;

  @Column(name = "strip_prefix", nullable = false)
  @Builder.Default
  private boolean stripPrefix = true;

  @Column(nullable = false)
  @Builder.Default
  private int version = 1;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private Map<String, Object> policies = Map.of();

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<Map<String, Object>> routes = List.of();

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private Map<String, String> headers = Map.of();

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
