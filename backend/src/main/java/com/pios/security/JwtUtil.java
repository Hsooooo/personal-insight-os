package com.pios.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String OAUTH_ACCESS_TYPE = "oauth_access";

    /** Domain-separation label for deriving the OAuth access-token signing key from JWT_SECRET. */
    private static final String OAUTH_KEY_LABEL = "pios:oauth-access-token:v1";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration-seconds}")
    private long refreshExpirationSeconds;

    private SecretKey signingKey;
    private SecretKey oauthSigningKey;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes (set JWT_SECRET)");
        }
        if (refreshExpirationSeconds <= 0) {
            throw new IllegalStateException("jwt.refresh-expiration-seconds must be positive");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.oauthSigningKey = Keys.hmacShaKeyFor(deriveKey(secretBytes, OAUTH_KEY_LABEL));
    }

    private static byte[] deriveKey(byte[] secret, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive OAuth signing key", e);
        }
    }

    /** Refresh-token lifetime for the web login flow (single source of truth). */
    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }

    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    public String extractTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get("type", String.class);
    }

    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "access".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** Parses a web-login access token; empty when invalid/expired or not an access token. */
    public Optional<Claims> parseAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "access".equals(claims.get("type", String.class)) ? Optional.of(claims) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ---------------------------------------------------------------------
    // OAuth access tokens (MCP). Signed with a key derived from JWT_SECRET so
    // they can never be confused with web-login tokens and vice versa.
    // ---------------------------------------------------------------------

    public String generateOAuthAccessToken(String issuer, Long userId, String audience, String scope,
                                           String clientId, Long grantId, long ttlSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlSeconds * 1000L);
        return Jwts.builder()
                .header().type("at+jwt").and()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .audience().add(audience).and()
                .claim("scope", scope)
                .claim("client_id", clientId)
                .claim("typ", OAUTH_ACCESS_TYPE)
                .claim("gid", grantId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(oauthSigningKey)
                .compact();
    }

    /**
     * Verifies signature, expiry, typ, issuer and audience of an OAuth access token.
     * Grant revocation is NOT checked here.
     */
    public Optional<Claims> parseOAuthAccessToken(String token, String expectedIssuer, String expectedAudience) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(oauthSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!OAUTH_ACCESS_TYPE.equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }
            if (expectedIssuer != null && !expectedIssuer.equals(claims.getIssuer())) {
                return Optional.empty();
            }
            if (expectedAudience != null
                    && (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
