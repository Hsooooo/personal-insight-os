package com.pios.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "provider_connections",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider_type"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "connection_status", nullable = false)
    private String connectionStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_payload", columnDefinition = "jsonb")
    private Map<String, Object> authPayload;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sync_config", columnDefinition = "jsonb")
    private Map<String, Object> syncConfig;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
