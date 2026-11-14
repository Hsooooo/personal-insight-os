package com.pios.security;

import com.pios.oauth.service.OAuthTokenService;
import com.pios.service.ApiKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "i".repeat(40);

    private JwtUtil jwtUtil;
    private ApiKeyService apiKeyService;
    private OAuthTokenService oAuthTokenService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = JwtUtilTest.create("s".repeat(48));
        apiKeyService = mock(ApiKeyService.class);
        oAuthTokenService = mock(OAuthTokenService.class);
        InternalSecretVerifier verifier = new InternalSecretVerifier(SECRET);
        verifier.validate();
        filter = new JwtAuthenticationFilter(jwtUtil, apiKeyService, oAuthTokenService, verifier);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void userJwtGivesUserPrincipal() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("Authorization", "Bearer " + jwtUtil.generateToken(5L, "a@b.c"));

        Authentication auth = run(r);
        assertThat(auth.getPrincipal()).isEqualTo(5L);
        assertThat(PiosAuthorities.has(auth, PiosAuthorities.ROLE_USER)).isTrue();
        assertThat(PiosAuthorities.has(auth, PiosAuthorities.ROLE_MCP)).isFalse();
    }

    @Test
    void refreshJwtIsNotAccepted() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("Authorization", "Bearer " + jwtUtil.generateRefreshToken(5L, "a@b.c"));
        assertThat(run(r)).isNull();
    }

    @Test
    void oauthTokenWithoutInternalSecretIsRejected() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("Authorization", "Bearer some.oauth.token");

        assertThat(run(r)).isNull();
        verify(oAuthTokenService, never()).verifyAccessToken(anyString());
    }

    @Test
    void oauthTokenWithWrongInternalSecretIsRejected() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("Authorization", "Bearer some.oauth.token");
        r.addHeader(InternalSecretVerifier.HEADER, "x".repeat(40));

        assertThat(run(r)).isNull();
    }

    @Test
    void oauthTokenFailingVerificationIsRejected() throws Exception {
        // verifyAccessToken returns empty for wrong aud / revoked grant
        when(oAuthTokenService.verifyAccessToken("bad.aud.token")).thenReturn(Optional.empty());
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("Authorization", "Bearer bad.aud.token");
        r.addHeader(InternalSecretVerifier.HEADER, SECRET);

        assertThat(run(r)).isNull();
    }

    @Test
    void oauthTokenWithSecretGivesMcpPrincipalWithScopes() throws Exception {
        when(oAuthTokenService.verifyAccessToken("good.token")).thenReturn(Optional.of(
                new OAuthTokenService.AccessTokenInfo(9L, "mcp:read", "client", 1L, Instant.now().plusSeconds(60), "aud")));
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("Authorization", "Bearer good.token");
        r.addHeader(InternalSecretVerifier.HEADER, SECRET);

        Authentication auth = run(r);
        assertThat(auth.getPrincipal()).isEqualTo(9L);
        assertThat(PiosAuthorities.has(auth, PiosAuthorities.ROLE_MCP)).isTrue();
        assertThat(PiosAuthorities.has(auth, PiosAuthorities.SCOPE_MCP_READ)).isTrue();
        assertThat(PiosAuthorities.has(auth, PiosAuthorities.SCOPE_MCP_WRITE)).isFalse();
    }

    @Test
    void apiKeyAcceptedViaBearerAndHeader() throws Exception {
        when(apiKeyService.authenticate(eq("pios_abc"), any()))
                .thenReturn(Optional.of(new ApiKeyService.ApiKeyPrincipal(3L, 11L)));

        MockHttpServletRequest bearer = new MockHttpServletRequest("GET", "/api/goals");
        bearer.addHeader("Authorization", "Bearer pios_abc");
        Authentication a1 = run(bearer);
        assertThat(a1.getPrincipal()).isEqualTo(11L);
        assertThat(PiosAuthorities.has(a1, PiosAuthorities.ROLE_MCP)).isTrue();
        assertThat(PiosAuthorities.has(a1, PiosAuthorities.SCOPE_MCP_WRITE)).isTrue();

        SecurityContextHolder.clearContext();
        MockHttpServletRequest header = new MockHttpServletRequest("GET", "/api/goals");
        header.addHeader("X-API-Key", "pios_abc");
        assertThat(run(header).getPrincipal()).isEqualTo(11L);
    }

    @Test
    void unknownApiKeyIsRejected() throws Exception {
        when(apiKeyService.authenticate(anyString(), any())).thenReturn(Optional.empty());
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/goals");
        r.addHeader("X-API-Key", "pios_nope");
        assertThat(run(r)).isNull();
    }
}
