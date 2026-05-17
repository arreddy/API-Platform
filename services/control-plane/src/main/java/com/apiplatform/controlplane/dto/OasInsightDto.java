package com.apiplatform.controlplane.dto;

import com.apiplatform.controlplane.entity.OasInsight;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OasInsightDto {

  public record SpectralResult(
      List<Map<String, Object>> violations,
      int errorCount,
      int warningCount,
      int infoCount,
      int hintCount) {}

  public record AiResult(
      Integer score,
      String summary,
      List<Map<String, Object>> risks,
      List<Map<String, Object>> suggestions) {}

  public record Full(
      String apiId,
      SpectralResult spectral,
      AiResult ai,
      OffsetDateTime analyzedAt) {

    public static Full from(OasInsight i) {
      return new Full(
          i.getApiId(),
          new SpectralResult(
              i.getSpectralViolations(),
              i.getSpectralErrorCount(),
              i.getSpectralWarningCount(),
              i.getSpectralInfoCount(),
              i.getSpectralHintCount()),
          new AiResult(
              i.getAiScore(),
              i.getAiSummary(),
              i.getAiRisks(),
              i.getAiSuggestions()),
          i.getAnalyzedAt());
    }
  }
}
