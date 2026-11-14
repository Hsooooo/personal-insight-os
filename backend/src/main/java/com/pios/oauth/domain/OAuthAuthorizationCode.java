package com.pios.oauth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Single-use authorization code (stored as SHA-256 hex). */
@Entity
@Table(name = "oauth_authorization_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthAuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_id", nullable = false, length = 2048)
    private String clientId;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "redirect_uri", nullable = false, length = 2048)
    private String redirectUri;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(name = "scope", nullable = false, length = 255)
    private String scope;

    @Column(name = "resource", nullable = false, length = 2048)
    private String resource;

    /** Grant created when the code was exchanged (used to revoke tokens on replay). */
    @Column(name = "grant_id")
    private Long grantId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
