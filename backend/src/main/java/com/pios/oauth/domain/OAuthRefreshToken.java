package com.pios.oauth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Opaque refresh token (stored as SHA-256 hex). used_at = rotated (kept for reuse detection). */
@Entity
@Table(name = "oauth_refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "grant_id", nullable = false)
    private Long grantId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
