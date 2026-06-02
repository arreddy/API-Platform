package com.apiplatform.controlplane.entity;

import com.apiplatform.controlplane.converter.UuidStringConverter;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "oas_insights")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OasInsight {

  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private String id;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "api_id", nullable = false, columnDefinition = "uuid")
  private String apiId;

  @Convert(converter = UuidStringConverter.class)
  @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
  private String tenantId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "spectral_violations", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<Map<String, Object>> spectralViolations = List.of();

  @Column(name = "spectral_error_count", nullable = false)
  @Builder.Default
  private int spectralErrorCount = 0;

  @Column(name = "spectral_warning_count", nullable = false)
  @Builder.Default
  private int spectralWarningCount = 0;

  @Column(name = "spectral_info_count", nullable = false)
  @Builder.Default
  private int spectralInfoCount = 0;

  @Column(name = "spectral_hint_count", nullable = false)
  @Builder.Default
  private int spectralHintCount = 0;

  @Column(name = "ai_score")
  private Integer aiScore;

  @Column(name = "ai_summary", columnDefinition = "text")
  private String aiSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ai_risks", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<Map<String, Object>> aiRisks = List.of();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ai_suggestions", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<Map<String, Object>> aiSuggestions = List.of();

  @Column(name = "analyzed_at", nullable = false)
  @Builder.Default
  private OffsetDateTime analyzedAt = OffsetDateTime.now();

  @PrePersist
  void prePersist() {
    if (this.id == null) this.id = UUID.randomUUID().toString();
  }
}
