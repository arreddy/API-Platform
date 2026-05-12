package com.apiplatform.controlplane.service;

import com.apiplatform.controlplane.dto.AnalyticsDto;
import com.apiplatform.controlplane.dto.PageDto;
import com.apiplatform.controlplane.entity.RequestLog;
import com.apiplatform.controlplane.repository.RequestLogRepository;
import java.time.OffsetDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

  private final RequestLogRepository logRepository;

  @Async
  @Transactional
  public void ingestBatch(List<AnalyticsDto.LogEntry> entries) {
    List<RequestLog> logs =
        entries.stream()
            .map(
                e ->
                    RequestLog.builder()
                        .tenantId(e.tenantId())
                        .proxyId(e.proxyId())
                        .apiKeyId(e.apiKeyId())
                        .method(e.method())
                        .path(e.path())
                        .queryParams(e.queryParams())
                        .statusCode(e.statusCode())
                        .latencyMs(e.latencyMs())
                        .requestSize(e.requestSize())
                        .responseSize(e.responseSize())
                        .clientIp(e.clientIp())
                        .userAgent(e.userAgent())
                        .errorMessage(e.errorMessage())
                        .build())
            .toList();
    logRepository.saveAll(logs);
  }

  public AnalyticsDto.Summary getSummary(
      String tenantId, String proxyId, OffsetDateTime from, OffsetDateTime to) {
    if (from == null) from = OffsetDateTime.now().minusHours(24);
    if (to == null) to = OffsetDateTime.now();

    long total = logRepository.countByTenantIdAndPeriod(tenantId, from, to);
    long success = logRepository.countSuccessByTenantIdAndPeriod(tenantId, from, to);
    long errors = logRepository.countErrorsByTenantIdAndPeriod(tenantId, from, to);
    Double avgLatency = logRepository.avgLatencyByTenantIdAndPeriod(tenantId, from, to);

    List<Map<String, Object>> timeSeries =
        buildTimeSeries(logRepository.timeSeriesByTenantId(tenantId, from, to));

    List<Map<String, Object>> statusDist =
        buildStatusDist(logRepository.statusDistribution(tenantId, from, to));

    return new AnalyticsDto.Summary(
        new AnalyticsDto.Totals(
            total, success, errors, avgLatency != null ? Math.round(avgLatency) : 0, 0),
        timeSeries,
        statusDist,
        Map.of("from", from.toString(), "to", to.toString()));
  }

  public PageDto<RequestLog> getRequests(String tenantId, String proxyId, int page, int size) {
    PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
    var result =
        proxyId != null
            ? logRepository.findByTenantIdAndProxyIdOrderByCreatedAtDesc(
                tenantId, proxyId, pageable)
            : logRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    return PageDto.of(result);
  }

  private List<Map<String, Object>> buildTimeSeries(List<Object[]> rows) {
    return rows.stream()
        .map(
            r -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("hour", r[0] != null ? r[0].toString() : null);
              m.put("requests", r[1]);
              m.put("avg_latency", r[2]);
              m.put("errors", r[3]);
              return m;
            })
        .toList();
  }

  private List<Map<String, Object>> buildStatusDist(List<Object[]> rows) {
    return rows.stream()
        .map(
            r -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("status_class", r[0]);
              m.put("count", r[1]);
              return m;
            })
        .toList();
  }
}
