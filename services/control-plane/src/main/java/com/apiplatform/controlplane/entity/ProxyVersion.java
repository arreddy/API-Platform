package com.apiplatform.controlplane.entity;

import com.apiplatform.controlplane.converter.UuidStringConverter;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "proxy_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyVersion {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private String id;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "proxy_id", nullable = false, columnDefinition = "uuid")
  private String proxyId;

  @Column(nullable = false)
  private int version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> snapshot;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "changed_by", columnDefinition = "uuid")
  private String changedBy;

  @Column(name = "change_note", columnDefinition = "text")
  private String changeNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Builder.Default
  private OffsetDateTime createdAt = OffsetDateTime.now();

  @PrePersist
  void prePersist() {
    if (this.id == null) this.id = UUID.randomUUID().toString();
  }
}
