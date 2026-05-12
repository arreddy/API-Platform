package com.apiplatform.controlplane.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsDto {

  public record LogEntry(
      String tenantId,
      String proxyId,
      String apiKeyId,
      String method,
      String path,
      Map<String, Object> queryParams,
      Integer statusCode,
      Integer latencyMs,
      Integer requestSize,
      Integer responseSize,
      String clientIp,
      String userAgent,
      String errorMessage) {}

  public record Totals(
      long totalRequests,
      long successRequests,
      long errorRequests,
      long avgLatency,
      long p99Latency) {}

  public record Summary(
      Totals totals,
      List<Map<String, Object>> timeSeries,
      List<Map<String, Object>> statusDistribution,
      Map<String, String> period) {}
}
