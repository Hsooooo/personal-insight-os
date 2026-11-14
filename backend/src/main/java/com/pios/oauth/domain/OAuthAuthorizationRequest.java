package com.pios.oauth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Pending /authorize request awaiting user consent. */
@Entity
@Table(name = "oauth_authorization_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthAuthorizationRequest {

    @Id
    @Column(name = "id", length = 64)
    private String id;

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

    @Column(name = "state", length = 2048)
    private String state;

    @Column(name = "resource", nullable = false, length = 2048)
    private String resource;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
