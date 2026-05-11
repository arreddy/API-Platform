package com.apiplatform.controlplane.entity;

import com.apiplatform.controlplane.converter.UuidStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private String id;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private String tenantId;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "proxy_id", columnDefinition = "uuid")
    private String proxyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(columnDefinition = "text[]")
    @Builder.Default
    private String[] scopes = new String[0];

    @Column(name = "rate_limit", nullable = false)
    @Builder.Default
    private int rateLimit = 1000;

    @Column(name = "rate_limit_window", nullable = false)
    @Builder.Default
    private String rateLimitWindow = "1h";

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Convert(converter = UuidStringConverter.class)
    @Column(name = "created_by", columnDefinition = "uuid")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void prePersist() {
        if (this.id == null) this.id = UUID.randomUUID().toString();
    }
}
