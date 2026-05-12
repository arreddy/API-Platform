package com.apiplatform.controlplane.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apiplatform.controlplane.dto.AnalyticsDto;
import com.apiplatform.controlplane.entity.RequestLog;
import com.apiplatform.controlplane.repository.RequestLogRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock RequestLogRepository logRepository;
  @InjectMocks AnalyticsService service;

  private static final String TENANT = "00000000-0000-0000-0000-000000000001";
  private static final String PROXY_ID = "proxy-1";

  private AnalyticsDto.LogEntry sampleEntry() {
    return new AnalyticsDto.LogEntry(
        TENANT, PROXY_ID, null, "GET", "/pets", Map.of(), 200, 42,
        100, 200, "127.0.0.1", "test-agent", null);
  }

  // ---- ingestBatch ----

  @Test
  void ingestBatch_savesAllMappedLogs() {
    var entries = List.of(sampleEntry(), sampleEntry());
    service.ingestBatch(entries);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RequestLog>> captor = ArgumentCaptor.forClass(List.class);
    verify(logRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
  }

  @Test
  void ingestBatch_mapsFieldsCorrectly() {
    service.ingestBatch(List.of(sampleEntry()));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RequestLog>> captor = ArgumentCaptor.forClass(List.class);
    verify(logRepository).saveAll(captor.capture());
    RequestLog log = captor.getValue().get(0);
    assertThat(log.getTenantId()).isEqualTo(TENANT);
    assertThat(log.getProxyId()).isEqualTo(PROXY_ID);
    assertThat(log.getMethod()).isEqualTo("GET");
    assertThat(log.getPath()).isEqualTo("/pets");
    assertThat(log.getStatusCode()).isEqualTo(200);
    assertThat(log.getLatencyMs()).isEqualTo(42);
  }

  @Test
  void ingestBatch_emptyList_savesNothing() {
    service.ingestBatch(List.of());
    verify(logRepository).saveAll(argThat(list -> !list.iterator().hasNext()));
  }

  // ---- getSummary ----

  @Test
  void getSummary_defaultsPeriodWhenNull() {
    stubSummaryRepo();
    // Should not throw even with null dates — defaults to last 24h
    assertThatCode(() -> service.getSummary(TENANT, null, null, null)).doesNotThrowAnyException();
  }

  @Test
  void getSummary_withExplicitDates_usesProvided() {
    stubSummaryRepo();
    OffsetDateTime from = OffsetDateTime.now().minusHours(1);
    OffsetDateTime to = OffsetDateTime.now();
    var result = service.getSummary(TENANT, null, from, to);
    assertThat(result.period().get("from")).isNotNull();
    assertThat(result.period().get("to")).isNotNull();
  }

  @Test
  void getSummary_returnsTotals() {
    when(logRepository.countByTenantIdAndPeriod(eq(TENANT), any(), any())).thenReturn(100L);
    when(logRepository.countSuccessByTenantIdAndPeriod(eq(TENANT), any(), any())).thenReturn(90L);
    when(logRepository.countErrorsByTenantIdAndPeriod(eq(TENANT), any(), any())).thenReturn(10L);
    when(logRepository.avgLatencyByTenantIdAndPeriod(eq(TENANT), any(), any())).thenReturn(55.5);
    when(logRepository.timeSeriesByTenantId(eq(TENANT), any(), any())).thenReturn(List.of());
    when(logRepository.statusDistribution(eq(TENANT), any(), any())).thenReturn(List.of());

    var result = service.getSummary(TENANT, null, null, null);
    assertThat(result.totals().totalRequests()).isEqualTo(100L);
    assertThat(result.totals().successRequests()).isEqualTo(90L);
    assertThat(result.totals().errorRequests()).isEqualTo(10L);
    assertThat(result.totals().avgLatency()).isEqualTo(56L); // Math.round(55.5)
  }

  @Test
  void getSummary_nullAvgLatency_defaultsToZero() {
    when(logRepository.countByTenantIdAndPeriod(any(), any(), any())).thenReturn(0L);
    when(logRepository.countSuccessByTenantIdAndPeriod(any(), any(), any())).thenReturn(0L);
    when(logRepository.countErrorsByTenantIdAndPeriod(any(), any(), any())).thenReturn(0L);
    when(logRepository.avgLatencyByTenantIdAndPeriod(any(), any(), any())).thenReturn(null);
    when(logRepository.timeSeriesByTenantId(any(), any(), any())).thenReturn(List.of());
    when(logRepository.statusDistribution(any(), any(), any())).thenReturn(List.of());

    var result = service.getSummary(TENANT, null, null, null);
    assertThat(result.totals().avgLatency()).isEqualTo(0L);
  }

  @Test
  void getSummary_buildsTimeSeriesFromRows() {
    Object[] row = new Object[] {"2024-01-01T00:00", 50L, 30.0, 5L};
    stubSummaryRepoWith(Collections.singletonList(row), List.of());
    var result = service.getSummary(TENANT, null, null, null);
    assertThat(result.timeSeries()).hasSize(1);
    assertThat(result.timeSeries().get(0).get("requests")).isEqualTo(50L);
    assertThat(result.timeSeries().get(0).get("errors")).isEqualTo(5L);
  }

  @Test
  void getSummary_buildsStatusDistFromRows() {
    Object[] row = new Object[] {"2xx", 80L};
    stubSummaryRepoWith(List.of(), Collections.singletonList(row));
    var result = service.getSummary(TENANT, null, null, null);
    assertThat(result.statusDistribution()).hasSize(1);
    assertThat(result.statusDistribution().get(0).get("status_class")).isEqualTo("2xx");
    assertThat(result.statusDistribution().get(0).get("count")).isEqualTo(80L);
  }

  // ---- getRequests ----

  @Test
  void getRequests_withProxyId_filtersCorrectly() {
    when(logRepository.findByTenantIdAndProxyIdOrderByCreatedAtDesc(eq(TENANT), eq(PROXY_ID), any()))
        .thenReturn(Page.empty());
    service.getRequests(TENANT, PROXY_ID, 1, 20);
    verify(logRepository)
        .findByTenantIdAndProxyIdOrderByCreatedAtDesc(eq(TENANT), eq(PROXY_ID), any());
  }

  @Test
  void getRequests_withoutProxyId_returnsAll() {
    when(logRepository.findByTenantIdOrderByCreatedAtDesc(eq(TENANT), any()))
        .thenReturn(Page.empty());
    service.getRequests(TENANT, null, 1, 20);
    verify(logRepository).findByTenantIdOrderByCreatedAtDesc(eq(TENANT), any());
  }

  @Test
  void getRequests_returnsPageDto() {
    RequestLog log = new RequestLog();
    when(logRepository.findByTenantIdOrderByCreatedAtDesc(eq(TENANT), any()))
        .thenReturn(new PageImpl<>(List.of(log)));
    var result = service.getRequests(TENANT, null, 1, 20);
    assertThat(result.data()).hasSize(1);
  }

  // ---- helpers ----

  private void stubSummaryRepo() {
    stubSummaryRepoWith(List.of(), List.of());
  }

  private void stubSummaryRepoWith(List<Object[]> timeSeries, List<Object[]> statusDist) {
    when(logRepository.countByTenantIdAndPeriod(any(), any(), any())).thenReturn(0L);
    when(logRepository.countSuccessByTenantIdAndPeriod(any(), any(), any())).thenReturn(0L);
    when(logRepository.countErrorsByTenantIdAndPeriod(any(), any(), any())).thenReturn(0L);
    when(logRepository.avgLatencyByTenantIdAndPeriod(any(), any(), any())).thenReturn(null);
    when(logRepository.timeSeriesByTenantId(any(), any(), any())).thenReturn(timeSeries);
    when(logRepository.statusDistribution(any(), any(), any())).thenReturn(statusDist);
  }
}
