package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.dto.OasInsightDto;
import com.apiplatform.controlplane.entity.OasInsight;
import com.apiplatform.controlplane.repository.OasInsightRepository;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class OasAnalysisService {

  private final OasInsightRepository insightRepository;

  @Value("${app.oas-analyzer-url:http://localhost:3004}")
  private String analyzerUrl;

  private RestClient analyzerClient;

  public OasAnalysisService(OasInsightRepository insightRepository) {
    this.insightRepository = insightRepository;
  }

  @PostConstruct
  void init() {
    analyzerClient = RestClient.builder().baseUrl(analyzerUrl).build();
  }

  @SuppressWarnings("unchecked")
  public OasInsightDto.Full analyze(String apiId, String tenantId, String oasContent) {
    try {
      Map<?, ?> response = analyzerClient.post()
          .uri("/analyze")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("oasContent", oasContent))
          .retrieve()
          .body(Map.class);

      if (response == null) throw new IllegalStateException("empty response from oas-analyzer");

      Map<String, Object> spectralData = (Map<String, Object>) response.get("spectral");
      Map<String, Object> aiData      = (Map<String, Object>) response.get("ai");

      OasInsight insight = insightRepository.findByApiId(apiId)
          .orElse(OasInsight.builder().apiId(apiId).tenantId(tenantId).build());

      if (spectralData != null) {
        insight.setSpectralViolations(castList(spectralData.get("violations")));
        insight.setSpectralErrorCount(toInt(spectralData.get("errorCount")));
        insight.setSpectralWarningCount(toInt(spectralData.get("warningCount")));
        insight.setSpectralInfoCount(toInt(spectralData.get("infoCount")));
        insight.setSpectralHintCount(toInt(spectralData.get("hintCount")));
      }

      if (aiData != null) {
        Object scoreVal = aiData.get("score");
        insight.setAiScore(scoreVal instanceof Number n ? n.intValue() : null);
        insight.setAiSummary((String) aiData.get("summary"));
        insight.setAiRisks(castList(aiData.get("risks")));
        insight.setAiSuggestions(castList(aiData.get("suggestions")));
      }

      insight.setAnalyzedAt(OffsetDateTime.now());
      return OasInsightDto.Full.from(insightRepository.save(insight));

    } catch (Exception e) {
      log.warn("OAS analysis failed for apiId={}: {}", apiId, e.getMessage());
      return null;
    }
  }

  public OasInsightDto.Full getInsight(String tenantId, String apiId) {
    return insightRepository.findByApiIdAndTenantId(apiId, tenantId)
        .map(OasInsightDto.Full::from)
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> castList(Object obj) {
    if (obj instanceof List<?> list) return (List<Map<String, Object>>) list;
    return List.of();
  }

  private int toInt(Object v) {
    return v instanceof Number n ? n.intValue() : 0;
  }
}
