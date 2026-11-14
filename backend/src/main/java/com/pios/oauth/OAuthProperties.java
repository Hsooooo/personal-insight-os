package com.pios.oauth;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Getter
@Component
public class OAuthProperties {

    @Value("${oauth.issuer}")
    private String issuer;

    @Value("${oauth.resource}")
    private String resource;

    @Value("${oauth.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${oauth.access-token-ttl-seconds:3600}")
    private long accessTokenTtlSeconds;

    @Value("${oauth.refresh-token-ttl-seconds:2592000}")
    private long refreshTokenTtlSeconds;

    @Value("${oauth.code-ttl-seconds:300}")
    private long codeTtlSeconds;

    @Value("${oauth.request-ttl-seconds:600}")
    private long requestTtlSeconds;

    @Value("${oauth.registration-rate-limit-per-hour:20}")
    private int registrationRateLimitPerHour;

    public OAuthProperties() {
    }

    /** For tests. */
    public OAuthProperties(String issuer, String resource, String frontendBaseUrl) {
        this.issuer = issuer;
        this.resource = resource;
        this.frontendBaseUrl = frontendBaseUrl;
        this.accessTokenTtlSeconds = 3600;
        this.refreshTokenTtlSeconds = 2592000;
        this.codeTtlSeconds = 300;
        this.requestTtlSeconds = 600;
        this.registrationRateLimitPerHour = 20;
        init();
    }

    @PostConstruct
    void init() {
        issuer = stripTrailingSlash(issuer);
        frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        URI.create(issuer);
        URI.create(resource);
        URI.create(frontendBaseUrl);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    public String endpoint(String path) {
        return issuer + path;
    }
}
