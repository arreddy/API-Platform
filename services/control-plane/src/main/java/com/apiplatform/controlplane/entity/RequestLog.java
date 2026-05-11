package com.apiplatform.controlplane.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import com.apiplatform.controlplane.converter.UuidStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "request_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private String id;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private String tenantId;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "proxy_id", columnDefinition = "uuid")
    private String proxyId;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "api_key_id", columnDefinition = "uuid")
    private String apiKeyId;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, columnDefinition = "text")
    private String path;

    @Type(JsonType.class)
    @Column(name = "query_params", columnDefinition = "jsonb")
    private Map<String, Object> queryParams;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "request_size")
    private Integer requestSize;

    @Column(name = "response_size")
    private Integer responseSize;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
    }
}
