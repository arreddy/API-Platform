package com.apiplatform.controlplane.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

@Entity
@Table(name = "api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "proxy_id")
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

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
