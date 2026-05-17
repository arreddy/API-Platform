package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apiplatform.controlplane.dto.OasInsightDto;
import com.apiplatform.controlplane.entity.OasInsight;
import com.apiplatform.controlplane.repository.OasInsightRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OasAnalysisServiceTest {

  @Mock OasInsightRepository insightRepository;
  @Mock RestClient analyzerClient;
  @Mock RequestBodyUriSpec postSpec;
  @Mock ResponseSpec responseSpec;

  // RETURNS_SELF makes all fluent methods (contentType, body, accept…) return the mock itself.
  // Only retrieve() — which exits to ResponseSpec — needs an explicit stub.
  RequestBodySpec bodySpec = mock(RequestBodySpec.class, RETURNS_SELF);

  OasAnalysisService service;

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String API_ID  = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    service = new OasAnalysisService(insightRepository);
    ReflectionTestUtils.setField(service, "analyzerClient", analyzerClient);

    when(analyzerClient.post()).thenReturn(postSpec);
    when(postSpec.uri("/analyze")).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private OasInsight savedInsight() {
    return OasInsight.builder()
        .id(UUID.randomUUID().toString())
        .apiId(API_ID)
        .tenantId(TENANT)
        .spectralViolations(List.of())
        .spectralErrorCount(0)
        .spectralWarningCount(0)
        .spectralInfoCount(0)
        .spectralHintCount(0)
        .aiScore(80)
        .aiSummary("Good API.")
        .aiRisks(List.of())
        .aiSuggestions(List.of())
        .analyzedAt(OffsetDateTime.now())
        .build();
  }

  private Map<String, Object> analyzerResponse(int errorCount, int warningCount,
                                                Integer aiScore, String aiSummary) {
    return Map.of(
        "spectral", Map.of(
            "violations",   List.of(Map.of("code", "no-description", "message", "Missing desc",
                                           "severity", "warning", "path", "info")),
            "errorCount",   errorCount,
            "warningCount", warningCount,
            "infoCount",    0,
            "hintCount",    0),
        "ai", Map.of(
            "score",       aiScore != null ? aiScore : 0,
            "summary",     aiSummary != null ? aiSummary : "",
            "risks",       List.of(),
            "suggestions", List.of()));
  }

  // ── analyze ────────────────────────────────────────────────────────────────

  @Test
  void analyze_createsNewInsightWhenNoneExists() {
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(1, 0, 85, "Looks good."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> {
      OasInsight i = inv.getArgument(0);
      i.setId(UUID.randomUUID().toString());
      return i;
    });

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "openapi: 3.0.0");

    assertThat(result).isNotNull();
    assertThat(result.apiId()).isEqualTo(API_ID);
    verify(insightRepository).save(any(OasInsight.class));
  }

  @Test
  void analyze_updatesExistingInsightWhenFound() {
    OasInsight existing = savedInsight();
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(0, 1, 70, "Decent."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.of(existing));
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.analyze(API_ID, TENANT, "openapi: 3.0.0");

    ArgumentCaptor<OasInsight> captor = ArgumentCaptor.forClass(OasInsight.class);
    verify(insightRepository).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
  }

  @Test
  void analyze_mapsSpectralCountsCorrectly() {
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(2, 3, 60, "Needs work."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result.spectral().errorCount()).isEqualTo(2);
    assertThat(result.spectral().warningCount()).isEqualTo(3);
    assertThat(result.spectral().infoCount()).isEqualTo(0);
    assertThat(result.spectral().hintCount()).isEqualTo(0);
  }

  @Test
  void analyze_mapsSpectralViolationsList() {
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(1, 0, 80, "OK."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result.spectral().violations()).hasSize(1);
    assertThat(result.spectral().violations().get(0)).containsEntry("code", "no-description");
  }

  @Test
  void analyze_mapsAiFieldsCorrectly() {
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(0, 0, 92, "Production-ready."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result.ai().score()).isEqualTo(92);
    assertThat(result.ai().summary()).isEqualTo("Production-ready.");
    assertThat(result.ai().risks()).isEmpty();
    assertThat(result.ai().suggestions()).isEmpty();
  }

  @Test
  void analyze_handlesNullAiScore() {
    Map<String, Object> response = Map.of(
        "spectral", Map.of("violations", List.of(), "errorCount", 0,
                           "warningCount", 0, "infoCount", 0, "hintCount", 0),
        "ai", Map.of("summary", "No score.", "risks", List.of(), "suggestions", List.of()));
    when(responseSpec.body(Map.class)).thenReturn(response);
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result.ai().score()).isNull();
  }

  @Test
  void analyze_setsAnalyzedAtToNow() {
    OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(0, 0, 80, "OK."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result.analyzedAt()).isAfter(before);
  }

  @Test
  void analyze_returnsNullWhenAnalyzerClientThrows() {
    when(responseSpec.body(Map.class)).thenThrow(new RuntimeException("connection refused"));

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result).isNull();
    verify(insightRepository, never()).save(any());
  }

  @Test
  void analyze_returnsNullWhenResponseIsNull() {
    when(responseSpec.body(Map.class)).thenReturn(null);

    OasInsightDto.Full result = service.analyze(API_ID, TENANT, "oas");

    assertThat(result).isNull();
    verify(insightRepository, never()).save(any());
  }

  @Test
  void analyze_sendsOasContentInRequestBody() {
    when(responseSpec.body(Map.class)).thenReturn(analyzerResponse(0, 0, 80, "OK."));
    when(insightRepository.findByApiId(API_ID)).thenReturn(Optional.empty());
    when(insightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.analyze(API_ID, TENANT, "openapi: 3.0.0");

    ArgumentCaptor<Map<?, ?>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
    verify(bodySpec).body(bodyCaptor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> captured = (Map<String, Object>) bodyCaptor.getValue();
    assertThat(captured).containsEntry("oasContent", "openapi: 3.0.0");
  }

  // ── getInsight ──────────────────────────────────────────────────────────────

  @Test
  void getInsight_returnsFullDtoWhenFound() {
    OasInsight insight = savedInsight();
    when(insightRepository.findByApiIdAndTenantId(API_ID, TENANT)).thenReturn(Optional.of(insight));

    OasInsightDto.Full result = service.getInsight(TENANT, API_ID);

    assertThat(result).isNotNull();
    assertThat(result.apiId()).isEqualTo(API_ID);
    assertThat(result.ai().score()).isEqualTo(80);
    assertThat(result.ai().summary()).isEqualTo("Good API.");
  }

  @Test
  void getInsight_returnsNullWhenNotFound() {
    when(insightRepository.findByApiIdAndTenantId(API_ID, TENANT)).thenReturn(Optional.empty());

    OasInsightDto.Full result = service.getInsight(TENANT, API_ID);

    assertThat(result).isNull();
  }
}
