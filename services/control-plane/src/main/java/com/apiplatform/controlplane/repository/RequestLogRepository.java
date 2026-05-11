package com.apiplatform.controlplane.repository;

import com.apiplatform.controlplane.entity.RequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface RequestLogRepository extends JpaRepository<RequestLog, String> {

    Page<RequestLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Page<RequestLog> findByTenantIdAndProxyIdOrderByCreatedAtDesc(String tenantId, String proxyId, Pageable pageable);

    @Query("""
        SELECT COUNT(r) FROM RequestLog r
        WHERE r.tenantId = :tenantId
          AND r.createdAt BETWEEN :from AND :to
    """)
    long countByTenantIdAndPeriod(String tenantId, OffsetDateTime from, OffsetDateTime to);

    @Query("""
        SELECT COUNT(r) FROM RequestLog r
        WHERE r.tenantId = :tenantId
          AND r.statusCode < 400
          AND r.createdAt BETWEEN :from AND :to
    """)
    long countSuccessByTenantIdAndPeriod(String tenantId, OffsetDateTime from, OffsetDateTime to);

    @Query("""
        SELECT COUNT(r) FROM RequestLog r
        WHERE r.tenantId = :tenantId
          AND r.statusCode >= 400
          AND r.createdAt BETWEEN :from AND :to
    """)
    long countErrorsByTenantIdAndPeriod(String tenantId, OffsetDateTime from, OffsetDateTime to);

    @Query("""
        SELECT AVG(r.latencyMs) FROM RequestLog r
        WHERE r.tenantId = :tenantId
          AND r.createdAt BETWEEN :from AND :to
    """)
    Double avgLatencyByTenantIdAndPeriod(String tenantId, OffsetDateTime from, OffsetDateTime to);

    @Query(value = """
        SELECT date_trunc('hour', created_at) AS hour,
               COUNT(*) AS requests,
               AVG(latency_ms)::numeric(10,2) AS avg_latency,
               COUNT(*) FILTER (WHERE status_code >= 400) AS errors
        FROM request_logs
        WHERE tenant_id = :tenantId
          AND created_at BETWEEN :from AND :to
        GROUP BY date_trunc('hour', created_at)
        ORDER BY hour
    """, nativeQuery = true)
    List<Object[]> timeSeriesByTenantId(String tenantId, OffsetDateTime from, OffsetDateTime to);

    @Query(value = """
        SELECT FLOOR(status_code / 100) * 100 AS status_class, COUNT(*) AS count
        FROM request_logs
        WHERE tenant_id = :tenantId
          AND created_at BETWEEN :from AND :to
        GROUP BY FLOOR(status_code / 100) * 100
        ORDER BY status_class
    """, nativeQuery = true)
    List<Object[]> statusDistribution(String tenantId, OffsetDateTime from, OffsetDateTime to);
}
