package com.pios.oauth.service;

import com.pios.oauth.OAuthException;
import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthTokens;
import com.pios.oauth.domain.OAuthAuthorizationCode;
import com.pios.oauth.domain.OAuthGrant;
import com.pios.oauth.domain.OAuthRefreshToken;
import com.pios.oauth.repository.OAuthAuthorizationCodeRepository;
import com.pios.oauth.repository.OAuthGrantRepository;
import com.pios.oauth.repository.OAuthRefreshTokenRepository;
import com.pios.security.JwtUtil;
import com.pios.security.JwtUtilTest;
import com.pios.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises code exchange / refresh rotation against in-memory fake repositories. */
class OAuthTokenServiceTest {

    private static final String ISSUER = "https://pios.example";
    private static final String RESOURCE = "https://pios.example/mcp";
    private static final String CLIENT = "mcp_client";
    private static final String REDIRECT = "https://app.example/cb";

    private final Map<String, OAuthAuthorizationCode> codes = new HashMap<>();
    private final Map<Long, OAuthGrant> grants = new HashMap<>();
    private final Map<String, OAuthRefreshToken> refreshTokens = new HashMap<>();
    private final AtomicLong ids = new AtomicLong(1);

    private OAuthTokenService service;
    private ApiKeyService apiKeyService;

    private String verifier;

    @BeforeEach
    void setUp() {
        OAuthAuthorizationCodeRepository codeRepo = mock(OAuthAuthorizationCodeRepository.class);
        when(codeRepo.findByCodeHashForUpdate(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(codes.get(inv.<String>getArgument(0))));
        when(codeRepo.save(any())).thenAnswer(inv -> {
            OAuthAuthorizationCode c = inv.getArgument(0);
            codes.put(c.getCodeHash(), c);
            return c;
        });

        OAuthGrantRepository grantRepo = mock(OAuthGrantRepository.class);
        when(grantRepo.save(any())).thenAnswer(inv -> {
            OAuthGrant g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId(ids.getAndIncrement());
                g.setCreatedAt(Instant.now());
            }
            grants.put(g.getId(), g);
            return g;
        });
        when(grantRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(grants.get(inv.<Long>getArgument(0))));
        when(grantRepo.existsByIdAndRevokedAtIsNull(anyLong())).thenAnswer(inv -> {
            OAuthGrant g = grants.get(inv.<Long>getArgument(0));
            return g != null && g.getRevokedAt() == null;
        });
        when(grantRepo.revoke(anyLong(), any())).thenAnswer(inv -> {
            OAuthGrant g = grants.get(inv.<Long>getArgument(0));
            if (g != null && g.getRevokedAt() == null) {
                g.setRevokedAt(inv.getArgument(1));
                return 1;
            }
            return 0;
        });

        OAuthRefreshTokenRepository rtRepo = mock(OAuthRefreshTokenRepository.class);
        when(rtRepo.save(any())).thenAnswer(inv -> {
            OAuthRefreshToken t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(ids.getAndIncrement());
            }
            refreshTokens.put(t.getTokenHash(), t);
            return t;
        });
        when(rtRepo.findByTokenHashForUpdate(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(refreshTokens.get(inv.<String>getArgument(0))));
        when(rtRepo.findByTokenHash(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(refreshTokens.get(inv.<String>getArgument(0))));
        when(rtRepo.revokeByGrantId(anyLong(), any())).thenAnswer(inv -> {
            Long gid = inv.getArgument(0);
            refreshTokens.values().stream().filter(t -> t.getGrantId().equals(gid) && t.getRevokedAt() == null)
                    .forEach(t -> t.setRevokedAt(inv.getArgument(1)));
            return 1;
        });

        JwtUtil jwtUtil = JwtUtilTest.create("s".repeat(48));
        apiKeyService = mock(ApiKeyService.class);
        service = new OAuthTokenService(new OAuthProperties(ISSUER, RESOURCE, ISSUER),
                codeRepo, grantRepo, rtRepo, jwtUtil, apiKeyService);

        verifier = OAuthTokens.randomToken(48);
    }

    private String issueCode(String challenge, Instant expiresAt) {
        String code = OAuthTokens.randomToken(32);
        codes.put(OAuthTokens.sha256Hex(code), OAuthAuthorizationCode.builder()
                .id(ids.getAndIncrement())
                .codeHash(OAuthTokens.sha256Hex(code))
                .userId(42L)
                .clientId(CLIENT)
                .clientName("Test App")
                .redirectUri(REDIRECT)
                .codeChallenge(challenge)
                .scope("mcp:read mcp:write")
                .resource(RESOURCE)
                .expiresAt(expiresAt)
                .build());
        return code;
    }

    private String issueCode() {
        return issueCode(OAuthTokens.s256Challenge(verifier), Instant.now().plusSeconds(300));
    }

    private static void expectError(Runnable r, String error) {
        assertThatThrownBy(r::run).isInstanceOf(OAuthException.class)
                .extracting(e -> ((OAuthException) e).getError()).isEqualTo(error);
    }

    @Test
    void exchangeIssuesTokensBoundToResource() {
        Map<String, Object> body = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, RESOURCE);

        assertThat(body.get("token_type")).isEqualTo("Bearer");
        assertThat(body.get("expires_in")).isEqualTo(3600L);
        assertThat(body.get("scope")).isEqualTo("mcp:read mcp:write");
        assertThat(body.get("refresh_token")).isNotNull();

        var info = service.verifyAccessToken((String) body.get("access_token")).orElseThrow();
        assertThat(info.userId()).isEqualTo(42L);
        assertThat(info.clientId()).isEqualTo(CLIENT);
        assertThat(grants.get(info.grantId()).getClientName()).isEqualTo("Test App");
    }

    @Test
    void wrongPkceVerifierIsRejectedAndBurnsCode() {
        String code = issueCode();
        expectError(() -> service.exchangeCode(code, REDIRECT, CLIENT, OAuthTokens.randomToken(48), null), "invalid_grant");
        // the correct verifier no longer works: code is single-use
        expectError(() -> service.exchangeCode(code, REDIRECT, CLIENT, verifier, null), "invalid_grant");
    }

    @Test
    void codeReplayRevokesIssuedTokens() {
        String code = issueCode();
        Map<String, Object> first = service.exchangeCode(code, REDIRECT, CLIENT, verifier, null);
        String access = (String) first.get("access_token");
        assertThat(service.verifyAccessToken(access)).isPresent();

        expectError(() -> service.exchangeCode(code, REDIRECT, CLIENT, verifier, null), "invalid_grant");

        assertThat(service.verifyAccessToken(access)).isEmpty();
        expectError(() -> service.refresh((String) first.get("refresh_token"), CLIENT, null), "invalid_grant");
    }

    @Test
    void redirectUriAndClientMustMatch() {
        expectError(() -> service.exchangeCode(issueCode(), "https://app.example/other", CLIENT, verifier, null),
                "invalid_grant");
        expectError(() -> service.exchangeCode(issueCode(), REDIRECT, "other_client", verifier, null), "invalid_grant");
    }

    @Test
    void expiredCodeIsRejected() {
        String code = issueCode(OAuthTokens.s256Challenge(verifier), Instant.now().minusSeconds(1));
        expectError(() -> service.exchangeCode(code, REDIRECT, CLIENT, verifier, null), "invalid_grant");
    }

    @Test
    void wrongResourceIsRejected() {
        expectError(() -> service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, "https://other/mcp"),
                "invalid_target");
    }

    @Test
    void missingParametersAreInvalidRequest() {
        expectError(() -> service.exchangeCode(issueCode(), REDIRECT, CLIENT, null, null), "invalid_request");
    }

    @Test
    void refreshRotatesToken() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        String rt1 = (String) first.get("refresh_token");

        Map<String, Object> second = service.refresh(rt1, CLIENT, null);
        String rt2 = (String) second.get("refresh_token");
        assertThat(rt2).isNotEqualTo(rt1);
        assertThat(service.verifyAccessToken((String) second.get("access_token"))).isPresent();

        Map<String, Object> third = service.refresh(rt2, CLIENT, null);
        assertThat(third.get("refresh_token")).isNotNull();
    }

    @Test
    void refreshTokenReuseRevokesWholeGrant() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        String rt1 = (String) first.get("refresh_token");
        Map<String, Object> second = service.refresh(rt1, CLIENT, null);
        String rt2 = (String) second.get("refresh_token");

        // attacker replays the old token after the 30s grace window
        refreshTokens.get(OAuthTokens.sha256Hex(rt1)).setUsedAt(Instant.now().minusSeconds(31));
        expectError(() -> service.refresh(rt1, CLIENT, null), "invalid_grant");

        // legitimate client's newer token and access tokens are dead too
        expectError(() -> service.refresh(rt2, CLIENT, null), "invalid_grant");
        assertThat(service.verifyAccessToken((String) second.get("access_token"))).isEmpty();
    }

    @Test
    void reusingJustRotatedTokenWithinGraceDoesNotRevokeGrant() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        String rt1 = (String) first.get("refresh_token");
        Map<String, Object> second = service.refresh(rt1, CLIENT, null);

        // client retries with the previous token right away (e.g. lost response)
        expectError(() -> service.refresh(rt1, CLIENT, null), "invalid_grant");

        // grant and the newest tokens remain valid
        assertThat(service.verifyAccessToken((String) second.get("access_token"))).isPresent();
        assertThat(service.refresh((String) second.get("refresh_token"), CLIENT, null)).containsKey("access_token");
    }

    @Test
    void refreshWithOtherClientIsRejected() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        expectError(() -> service.refresh((String) first.get("refresh_token"), "other", null), "invalid_grant");
    }

    @Test
    void refreshScopeMayOnlyNarrow() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        Map<String, Object> narrowed = service.refresh((String) first.get("refresh_token"), CLIENT, "mcp:read");
        assertThat(narrowed.get("scope")).isEqualTo("mcp:read");
        expectError(() -> service.refresh((String) narrowed.get("refresh_token"), CLIENT, "admin"), "invalid_scope");
    }

    @Test
    void revokeRefreshTokenRevokesGrant() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        service.revoke((String) first.get("refresh_token"), CLIENT);
        assertThat(service.verifyAccessToken((String) first.get("access_token"))).isEmpty();
        // unknown token is silently accepted
        service.revoke("garbage", CLIENT);
    }

    @Test
    void introspectOAuthToken() {
        Map<String, Object> first = service.exchangeCode(issueCode(), REDIRECT, CLIENT, verifier, null);
        Map<String, Object> result = service.introspect((String) first.get("access_token"), "10.0.0.1");

        assertThat(result.get("active")).isEqualTo(true);
        assertThat(result.get("sub")).isEqualTo("42");
        assertThat(result.get("aud")).isEqualTo(RESOURCE);
        assertThat(result.get("token_type")).isEqualTo("oauth");
        assertThat(result.get("exp")).isInstanceOf(Long.class);
    }

    @Test
    void introspectApiKey() {
        when(apiKeyService.authenticate(anyString(), any()))
                .thenReturn(Optional.of(new ApiKeyService.ApiKeyPrincipal(1L, 5L)));
        Map<String, Object> result = service.introspect("pios_somekey", "10.0.0.1");

        assertThat(result.get("active")).isEqualTo(true);
        assertThat(result.get("token_type")).isEqualTo("api_key");
        assertThat(result.get("scope")).isEqualTo("mcp:read mcp:write");
        assertThat(result).doesNotContainKey("exp");
    }

    @Test
    void introspectInactive() {
        assertThat(service.introspect("not-a-token", "x")).containsEntry("active", false).hasSize(1);
        assertThat(service.introspect(null, "x")).containsEntry("active", false);
    }
}
