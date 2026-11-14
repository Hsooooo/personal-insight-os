package com.pios.oauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pios.oauth.service.OAuthAuthorizationService;
import com.pios.oauth.service.OAuthClientService;
import com.pios.oauth.service.OAuthTokenService;
import com.pios.security.InternalSecretVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OAuthControllerTest {

    private static final String SECRET = "z".repeat(40);
    private OAuthTokenService tokenService;
    private OAuthController controller;

    @BeforeEach
    void setUp() {
        tokenService = mock(OAuthTokenService.class);
        when(tokenService.introspect(any(), any())).thenReturn(Map.of("active", true));
        InternalSecretVerifier verifier = new InternalSecretVerifier(SECRET);
        controller = new OAuthController(mock(OAuthAuthorizationService.class), mock(OAuthClientService.class),
                tokenService, verifier, new ObjectMapper());
    }

    @Test
    void introspectRequiresInternalSecret() throws Exception {
        MockHttpServletRequest noSecret = new MockHttpServletRequest("POST", "/api/oauth/introspect");
        noSecret.setParameter("token", "t");
        assertThat(controller.introspect(noSecret).getStatusCode().value()).isEqualTo(401);

        MockHttpServletRequest wrong = new MockHttpServletRequest("POST", "/api/oauth/introspect");
        wrong.addHeader(InternalSecretVerifier.HEADER, SECRET + "x");
        wrong.setParameter("token", "t");
        assertThat(controller.introspect(wrong).getStatusCode().value()).isEqualTo(401);

        verify(tokenService, never()).introspect(anyString(), any());
    }

    @Test
    void introspectAcceptsFormAndJson() throws Exception {
        MockHttpServletRequest form = new MockHttpServletRequest("POST", "/api/oauth/introspect");
        form.addHeader(InternalSecretVerifier.HEADER, SECRET);
        form.setParameter("token", "form-token");
        assertThat(controller.introspect(form).getStatusCode().value()).isEqualTo(200);
        verify(tokenService).introspect(eq("form-token"), any());

        MockHttpServletRequest json = new MockHttpServletRequest("POST", "/api/oauth/introspect");
        json.addHeader(InternalSecretVerifier.HEADER, SECRET);
        json.setContentType("application/json");
        json.setContent("{\"token\":\"json-token\"}".getBytes());
        assertThat(controller.introspect(json).getHeaders().getCacheControl()).contains("no-store");
        verify(tokenService).introspect(eq("json-token"), any());
    }

    @Test
    void tokenEndpointRejectsQueryStringAndRepeatedParams() {
        MockHttpServletRequest query = new MockHttpServletRequest("POST", "/api/oauth/token");
        query.setQueryString("grant_type=authorization_code");
        query.setParameter("grant_type", "authorization_code");
        assertThat(controller.handleOAuth(catchOAuth(() -> controller.token(query))).getBody())
                .containsEntry("error", "invalid_request");

        MockHttpServletRequest repeated = new MockHttpServletRequest("POST", "/api/oauth/token");
        repeated.setParameter("grant_type", "refresh_token", "authorization_code");
        assertThat(catchOAuth(() -> controller.token(repeated)).getError()).isEqualTo("invalid_request");

        MockHttpServletRequest revoke = new MockHttpServletRequest("POST", "/api/oauth/revoke");
        revoke.setQueryString("token=x");
        revoke.setParameter("token", "x");
        assertThat(catchOAuth(() -> controller.revoke(revoke)).getError()).isEqualTo("invalid_request");
        verify(tokenService, never()).revoke(any(), any());
    }

    @Test
    void introspectRejectsQueryString() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/oauth/introspect");
        r.addHeader(InternalSecretVerifier.HEADER, SECRET);
        r.setQueryString("token=t");
        r.setParameter("token", "t");
        assertThat(catchOAuth(() -> controller.introspect(r)).getError()).isEqualTo("invalid_request");
    }

    @Test
    void authorizeIsRateLimitedPerIpWithoutRedirect() {
        OAuthAuthorizationService authz = mock(OAuthAuthorizationService.class);
        when(authz.authorize(any(), any())).thenReturn("https://ui.example/oauth/consent?request_id=x");
        OAuthController c = new OAuthController(authz, mock(OAuthClientService.class), tokenService,
                new InternalSecretVerifier(SECRET), new ObjectMapper());
        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/api/oauth/authorize");
        http.setRemoteAddr("198.51.100.3");
        for (int i = 0; i < 60; i++) {
            assertThat(c.authorize("code", "c", "r", "x", "S256", null, null, null, http).getStatusCode().value())
                    .isEqualTo(302);
        }
        var limited = c.authorize("code", "c", "r", "x", "S256", null, null, null, http);
        assertThat(limited.getStatusCode().value()).isEqualTo(429);
        assertThat(limited.getHeaders().getLocation()).isNull();
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    interface Call {
        Object run() throws Exception;
    }

    private static com.pios.oauth.OAuthException catchOAuth(Call call) {
        try {
            call.run();
        } catch (com.pios.oauth.OAuthException e) {
            return e;
        } catch (Exception e) {
            throw new AssertionError("unexpected " + e, e);
        }
        throw new AssertionError("expected OAuthException");
    }
}
