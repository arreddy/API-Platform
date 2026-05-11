package com.apiplatform.controlplane.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "proxy_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyVersion {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private String id;

    @Column(name = "proxy_id", nullable = false)
    private String proxyId;

    @Column(nullable = false)
    private int version;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> snapshot;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "change_note", columnDefinition = "text")
    private String changeNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
