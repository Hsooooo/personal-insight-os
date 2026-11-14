package com.pios.oauth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Dynamically registered (RFC 7591) public client. */
@Entity
@Table(name = "oauth_clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true, length = 255)
    private String clientId;

    @Column(name = "client_name", length = 255)
    private String clientName;

    /** JSON array of redirect URIs */
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    private String redirectUris;

    /** space separated */
    @Column(name = "grant_types", nullable = false, length = 255)
    private String grantTypes;

    @Column(name = "scope", length = 255)
    private String scope;

    @Column(name = "token_endpoint_auth_method", nullable = false, length = 50)
    private String tokenEndpointAuthMethod;

    @Column(name = "registration_ip", length = 45)
    private String registrationIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
