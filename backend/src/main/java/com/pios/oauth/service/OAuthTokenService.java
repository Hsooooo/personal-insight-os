package com.pios.oauth.service;

import com.pios.oauth.OAuthException;
import com.pios.oauth.OAuthNotFoundException;
import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthScopes;
import com.pios.oauth.OAuthTokens;
import com.pios.oauth.RedirectUris;
import com.pios.oauth.domain.OAuthAuthorizationCode;
import com.pios.oauth.domain.OAuthGrant;
import com.pios.oauth.domain.OAuthRefreshToken;
import com.pios.oauth.dto.OAuthGrantResponse;
import com.pios.oauth.repository.OAuthAuthorizationCodeRepository;
import com.pios.oauth.repository.OAuthGrantRepository;
import com.pios.oauth.repository.OAuthRefreshTokenRepository;
import com.pios.security.JwtUtil;
import com.pios.service.ApiKeyService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthTokenService {

    private static final long GRANT_TOUCH_INTERVAL_MILLIS = 60_000;
    /** Re-presenting the previous refresh token within this window is rejected without revoking the grant. */
    static final java.time.Duration ROTATION_GRACE = java.time.Duration.ofSeconds(30);

    private final OAuthProperties properties;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final OAuthGrantRepository grantRepository;
    private final OAuthRefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final ApiKeyService apiKeyService;

    private final Map<Long, Long> grantLastTouch = new ConcurrentHashMap<>();

    /** Verified OAuth access token. */
    public record AccessTokenInfo(Long userId, String scope, String clientId, Long grantId, Instant expiresAt,
                                  String audience) {
    }

    // -------------------------------------------------------- authorization_code

    /**
     * Exchanges a code. The code is burned on the first attempt, even if verification fails.
     * Replay of a used code revokes the grant (and thus every token) issued from it.
     */
    @Transactional(noRollbackFor = OAuthException.class)
    public Map<String, Object> exchangeCode(String code, String redirectUri, String clientId,
                                            String codeVerifier, String resource) {
        require(code, "code");
        require(redirectUri, "redirect_uri");
        require(clientId, "client_id");
        require(codeVerifier, "code_verifier");

        OAuthAuthorizationCode stored = codeRepository.findByCodeHashForUpdate(OAuthTokens.sha256Hex(code))
                .orElseThrow(() -> OAuthException.invalidGrant("Invalid authorization code"));
        Instant now = Instant.now();

        if (stored.getUsedAt() != null) {
            if (stored.getGrantId() != null) {
                revokeGrantInternal(stored.getGrantId(), now);
                log.warn("Authorization code replay detected; revoked grant {}", stored.getGrantId());
            }
            throw OAuthException.invalidGrant("Authorization code already used");
        }
        stored.setUsedAt(now);
        codeRepository.save(stored);

        if (!stored.getExpiresAt().isAfter(now)) {
            throw OAuthException.invalidGrant("Authorization code expired");
        }
        if (!OAuthTokens.constantTimeEquals(clientId, stored.getClientId())) {
            throw OAuthException.invalidGrant("Code was issued to another client");
        }
        if (!RedirectUris.matches(redirectUri, stored.getRedirectUri())) {
            throw OAuthException.invalidGrant("redirect_uri mismatch");
        }
        if (!OAuthTokens.verifyPkce(codeVerifier, stored.getCodeChallenge())) {
            throw OAuthException.invalidGrant("PKCE verification failed");
        }
        if (resource != null && !resource.isBlank() && !resource.equals(stored.getResource())) {
            throw new OAuthException("invalid_target", "resource does not match the authorization request");
        }

        OAuthGrant grant = grantRepository.save(OAuthGrant.builder()
                .userId(stored.getUserId())
                .clientId(stored.getClientId())
                .clientName(stored.getClientName())
                .scope(stored.getScope())
                .resource(stored.getResource())
                .lastUsedAt(now)
                .build());
        stored.setGrantId(grant.getId());
        codeRepository.save(stored);

        return issueTokens(grant, stored.getScope(), now);
    }

    // ------------------------------------------------------------- refresh_token

    @Transactional(noRollbackFor = OAuthException.class)
    public Map<String, Object> refresh(String refreshToken, String clientId, String scope) {
        require(refreshToken, "refresh_token");
        require(clientId, "client_id");

        OAuthRefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(OAuthTokens.sha256Hex(refreshToken))
                .orElseThrow(() -> OAuthException.invalidGrant("Invalid refresh token"));
        Instant now = Instant.now();
        OAuthGrant grant = grantRepository.findById(stored.getGrantId())
                .orElseThrow(() -> OAuthException.invalidGrant("Invalid refresh token"));

        if (!OAuthTokens.constantTimeEquals(clientId, grant.getClientId())) {
            throw OAuthException.invalidGrant("Refresh token was issued to another client");
        }
        if (stored.getRevokedAt() == null && stored.getUsedAt() != null
                && stored.getUsedAt().isAfter(now.minus(ROTATION_GRACE))) {
            // Just-rotated token presented again (e.g. client retry after a lost response / concurrent refresh):
            // reject, but don't nuke the grant.
            throw OAuthException.invalidGrant("Refresh token was already rotated");
        }
        if (stored.getUsedAt() != null || stored.getRevokedAt() != null) {
            if (grant.isActive()) {
                revokeGrantInternal(grant.getId(), now);
                log.warn("Refresh token reuse detected; revoked grant {}", grant.getId());
            }
            throw OAuthException.invalidGrant("Refresh token is no longer valid");
        }
        if (!grant.isActive()) {
            throw OAuthException.invalidGrant("Authorization has been revoked");
        }
        if (!stored.getExpiresAt().isAfter(now)) {
            throw OAuthException.invalidGrant("Refresh token expired");
        }
        String effectiveScope = grant.getScope();
        if (scope != null && !scope.isBlank()) {
            String normalized = OAuthScopes.normalize(scope);
            if (normalized.isEmpty() || !OAuthScopes.isSubset(normalized, grant.getScope())) {
                throw new OAuthException("invalid_scope", "Requested scope exceeds the original grant");
            }
            effectiveScope = normalized;
        }

        stored.setUsedAt(now);
        refreshTokenRepository.save(stored);
        grant.setLastUsedAt(now);
        grantRepository.save(grant);

        return issueTokens(grant, effectiveScope, now);
    }

    private Map<String, Object> issueTokens(OAuthGrant grant, String scope, Instant now) {
        String refresh = OAuthTokens.randomToken(32);
        refreshTokenRepository.save(OAuthRefreshToken.builder()
                .tokenHash(OAuthTokens.sha256Hex(refresh))
                .grantId(grant.getId())
                .expiresAt(now.plusSeconds(properties.getRefreshTokenTtlSeconds()))
                .build());
        String access = jwtUtil.generateOAuthAccessToken(properties.getIssuer(), grant.getUserId(),
                grant.getResource(), scope, grant.getClientId(), grant.getId(), properties.getAccessTokenTtlSeconds());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", access);
        body.put("token_type", "Bearer");
        body.put("expires_in", properties.getAccessTokenTtlSeconds());
        body.put("refresh_token", refresh);
        body.put("scope", scope);
        return body;
    }

    // ------------------------------------------------------------------ revoke

    /** RFC 7009. Revoking either token type revokes the whole grant. Never fails for unknown tokens. */
    @Transactional
    public void revoke(String token, String clientId) {
        if (token == null || token.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        Optional<OAuthRefreshToken> refresh = refreshTokenRepository.findByTokenHash(OAuthTokens.sha256Hex(token));
        if (refresh.isPresent()) {
            grantRepository.findById(refresh.get().getGrantId())
                    .filter(g -> clientId == null || OAuthTokens.constantTimeEquals(clientId, g.getClientId()))
                    .ifPresent(g -> revokeGrantInternal(g.getId(), now));
            return;
        }
        jwtUtil.parseOAuthAccessToken(token, properties.getIssuer(), null).ifPresent(claims -> {
            Long grantId = grantIdOf(claims);
            String tokenClient = claims.get("client_id", String.class);
            if (grantId != null && (clientId == null || OAuthTokens.constantTimeEquals(clientId, tokenClient))) {
                revokeGrantInternal(grantId, now);
            }
        });
    }

    private void revokeGrantInternal(Long grantId, Instant now) {
        grantRepository.revoke(grantId, now);
        refreshTokenRepository.revokeByGrantId(grantId, now);
        grantLastTouch.remove(grantId);
    }

    // ---------------------------------------------------- access token checks

    /** Full verification incl. audience == MCP resource and grant still active. */
    @Transactional(readOnly = true)
    public Optional<AccessTokenInfo> verifyAccessToken(String token) {
        Optional<Claims> parsed = jwtUtil.parseOAuthAccessToken(token, properties.getIssuer(), properties.getResource());
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        Long grantId = grantIdOf(claims);
        if (grantId == null || !grantRepository.existsByIdAndRevokedAtIsNull(grantId)) {
            return Optional.empty();
        }
        Long userId;
        try {
            userId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new AccessTokenInfo(userId, claims.get("scope", String.class),
                claims.get("client_id", String.class), grantId, claims.getExpiration().toInstant(),
                properties.getResource()));
    }

    private static Long grantIdOf(Claims claims) {
        Object gid = claims.get("gid");
        if (gid instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /** RFC 7662-style introspection for the MCP server (OAuth access tokens and pios_ API keys). */
    @Transactional
    public Map<String, Object> introspect(String token, String clientIp) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (token == null || token.isBlank()) {
            body.put("active", false);
            return body;
        }
        if (ApiKeyService.looksLikeApiKey(token)) {
            Optional<ApiKeyService.ApiKeyPrincipal> key = apiKeyService.authenticate(token, clientIp);
            if (key.isEmpty()) {
                body.put("active", false);
                return body;
            }
            body.put("active", true);
            body.put("sub", String.valueOf(key.get().userId()));
            body.put("scope", OAuthScopes.DEFAULT);
            body.put("client_id", "pios_api_key");
            body.put("aud", properties.getResource());
            body.put("iss", properties.getIssuer());
            body.put("token_type", "api_key");
            return body;
        }
        Optional<AccessTokenInfo> info = verifyAccessToken(token);
        if (info.isEmpty()) {
            body.put("active", false);
            return body;
        }
        AccessTokenInfo t = info.get();
        touchGrant(t.grantId());
        body.put("active", true);
        body.put("sub", String.valueOf(t.userId()));
        body.put("scope", t.scope());
        body.put("client_id", t.clientId());
        body.put("exp", t.expiresAt().getEpochSecond());
        body.put("aud", t.audience());
        body.put("iss", properties.getIssuer());
        body.put("token_type", "oauth");
        return body;
    }

    private void touchGrant(Long grantId) {
        long now = System.currentTimeMillis();
        Long last = grantLastTouch.get(grantId);
        if (last != null && now - last < GRANT_TOUCH_INTERVAL_MILLIS) {
            return;
        }
        grantLastTouch.put(grantId, now);
        grantRepository.touch(grantId, Instant.now());
    }

    // ------------------------------------------------------------------ grants

    @Transactional(readOnly = true)
    public List<OAuthGrantResponse> listGrants(Long userId) {
        return grantRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(g -> OAuthGrantResponse.builder()
                        .id(g.getId())
                        .clientId(g.getClientId())
                        .clientName(g.getClientName())
                        .scopes(OAuthScopes.toList(g.getScope()))
                        .createdAt(g.getCreatedAt())
                        .lastUsedAt(g.getLastUsedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeGrant(Long userId, Long grantId) {
        OAuthGrant grant = grantRepository.findByIdAndUserId(grantId, userId)
                .filter(OAuthGrant::isActive)
                .orElseThrow(() -> new OAuthNotFoundException("Grant not found"));
        revokeGrantInternal(grant.getId(), Instant.now());
    }

    /** Drops throttle entries older than the touch interval (called by the cleanup job). */
    public int trimTouchCache() {
        long cutoff = System.currentTimeMillis() - GRANT_TOUCH_INTERVAL_MILLIS;
        int before = grantLastTouch.size();
        grantLastTouch.values().removeIf(t -> t < cutoff);
        return before - grantLastTouch.size();
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw OAuthException.invalidRequest(name + " is required");
        }
    }
}
