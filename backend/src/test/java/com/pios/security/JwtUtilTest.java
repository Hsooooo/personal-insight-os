package com.pios.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtUtilTest {

    private static final String ISS = "https://pios.example";
    private static final String AUD = "https://pios.example/mcp";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = create("s".repeat(48));
    }

    public static JwtUtil create(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", 60_000L);
        ReflectionTestUtils.setField(util, "refreshExpirationSeconds", 3600L);
        util.validateSecret();
        return util;
    }

    @Test
    void accessTokenIsValidOnlyAsAccessToken() {
        String token = jwtUtil.generateToken(7L, "me@example.com");

        assertThat(jwtUtil.validateAccessToken(token)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(token)).isFalse();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(7L);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String token = jwtUtil.generateRefreshToken(7L, "me@example.com");

        assertThat(jwtUtil.validateRefreshToken(token)).isTrue();
        assertThat(jwtUtil.validateAccessToken(token)).isFalse();
    }

    @Test
    void refreshTokensAreUniqueEvenWithinTheSameSecond() {
        assertThat(jwtUtil.generateRefreshToken(7L, "a@b.c")).isNotEqualTo(jwtUtil.generateRefreshToken(7L, "a@b.c"));
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String foreign = create("x".repeat(48)).generateToken(7L, "me@example.com");

        assertThat(jwtUtil.validateAccessToken(foreign)).isFalse();
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> create("short"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oauthAccessTokenRoundTrip() {
        String token = jwtUtil.generateOAuthAccessToken(ISS, 7L, AUD, "mcp:read", "client-1", 42L, 3600);

        var claims = jwtUtil.parseOAuthAccessToken(token, ISS, AUD).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("scope", String.class)).isEqualTo("mcp:read");
        assertThat(claims.get("client_id", String.class)).isEqualTo("client-1");
        assertThat(claims.get("typ", String.class)).isEqualTo("oauth_access");
        assertThat(((Number) claims.get("gid")).longValue()).isEqualTo(42L);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void oauthTokenWithWrongAudienceOrIssuerIsRejected() {
        String token = jwtUtil.generateOAuthAccessToken(ISS, 7L, "https://other/mcp", "mcp:read", "c", 1L, 3600);

        assertThat(jwtUtil.parseOAuthAccessToken(token, ISS, AUD)).isEmpty();
        assertThat(jwtUtil.parseOAuthAccessToken(token, "https://evil", null)).isEmpty();
    }

    @Test
    void oauthAndUserTokensAreNotInterchangeable() {
        String oauth = jwtUtil.generateOAuthAccessToken(ISS, 7L, AUD, "mcp:read", "c", 1L, 3600);
        String user = jwtUtil.generateToken(7L, "me@example.com");

        assertThat(jwtUtil.validateAccessToken(oauth)).isFalse();
        assertThat(jwtUtil.parseAccessToken(oauth)).isEmpty();
        assertThat(jwtUtil.parseOAuthAccessToken(user, ISS, AUD)).isEmpty();
    }

    @Test
    void expiredOAuthTokenIsRejected() {
        String token = jwtUtil.generateOAuthAccessToken(ISS, 7L, AUD, "mcp:read", "c", 1L, -10);

        assertThat(jwtUtil.parseOAuthAccessToken(token, ISS, AUD)).isEmpty();
    }
}
