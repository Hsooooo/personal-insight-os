package com.pios.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = create("s".repeat(48));
    }

    private JwtUtil create(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", 60_000L);
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
    void tokenSignedWithOtherSecretIsRejected() {
        String foreign = create("x".repeat(48)).generateToken(7L, "me@example.com");

        assertThat(jwtUtil.validateAccessToken(foreign)).isFalse();
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> create("short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
