package com.pios.oauth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A user's authorization of a client. Refresh tokens and access tokens (gid claim) reference it. */
@Entity
@Table(name = "oauth_grants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id", nullable = false, length = 2048)
    private String clientId;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "scope", nullable = false, length = 255)
    private String scope;

    @Column(name = "resource", nullable = false, length = 2048)
    private String resource;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
