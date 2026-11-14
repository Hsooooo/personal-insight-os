package com.pios.oauth.service;

import com.pios.oauth.AuthorizationRedirectException;
import com.pios.oauth.OAuthException;
import com.pios.oauth.OAuthNotFoundException;
import com.pios.oauth.OAuthProperties;
import com.pios.oauth.OAuthTokens;
import com.pios.oauth.domain.OAuthAuthorizationCode;
import com.pios.oauth.domain.OAuthAuthorizationRequest;
import com.pios.oauth.repository.OAuthAuthorizationCodeRepository;
import com.pios.oauth.repository.OAuthAuthorizationRequestRepository;
import com.pios.oauth.repository.OAuthGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OAuthAuthorizationServiceTest {

    private static final String ISSUER = "https://pios.example";
    private static final String RESOURCE = ISSUER + "/mcp";
    private static final String REDIRECT = "https://app.example/cb";
    private static final String CHALLENGE = OAuthTokens.s256Challenge(OAuthTokens.randomToken(48));

    private OAuthClientService clientService;
    private OAuthAuthorizationRequestRepository requestRepo;
    private OAuthAuthorizationCodeRepository codeRepo;
    private OAuthGrantRepository grantRepo;
    private OAuthAuthorizationService service;
    private final Map<String, OAuthAuthorizationRequest> requests = new HashMap<>();

    @BeforeEach
    void setUp() {
        clientService = mock(OAuthClientService.class);
        requestRepo = mock(OAuthAuthorizationRequestRepository.class);
        codeRepo = mock(OAuthAuthorizationCodeRepository.class);
        grantRepo = mock(OAuthGrantRepository.class);
        when(grantRepo.existsByClientId("client")).thenReturn(true);
        when(requestRepo.save(any())).thenAnswer(inv -> {
            OAuthAuthorizationRequest r = inv.getArgument(0);
            requests.put(r.getId(), r);
            return r;
        });
        when(requestRepo.findByIdForUpdate(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<String>getArgument(0))));
        when(clientService.resolve(eq("client"), any())).thenReturn(
                new ResolvedClient("client", "App", List.of(REDIRECT), false));
        when(clientService.resolve(eq("unknown"), any())).thenThrow(OAuthException.invalidClient("Unknown client_id"));
        service = new OAuthAuthorizationService(new OAuthProperties(ISSUER, RESOURCE, "https://ui.example"),
                clientService, requestRepo, codeRepo, grantRepo);
    }

    private OAuthAuthorizationService.AuthorizeParams params(String clientId, String redirect, String method,
                                                             String scope, String resource) {
        return new OAuthAuthorizationService.AuthorizeParams("code", clientId, redirect, CHALLENGE, method,
                "st&ate", scope, resource);
    }

    @Test
    void validRequestRedirectsToConsentPage() {
        String url = service.authorize(params("client", REDIRECT, "S256", null, RESOURCE), "1.1.1.1");
        assertThat(url).startsWith("https://ui.example/oauth/consent?request_id=");
        OAuthAuthorizationRequest stored = requests.values().iterator().next();
        assertThat(stored.getScope()).isEqualTo("mcp:read mcp:write");
        assertThat(stored.getResource()).isEqualTo(RESOURCE);
    }

    @Test
    void unknownClientOrUnregisteredRedirectShowsErrorWithoutRedirect() {
        assertThatThrownBy(() -> service.authorize(params("unknown", REDIRECT, "S256", null, null), "1.1.1.1"))
                .isInstanceOf(OAuthException.class);
        assertThatThrownBy(() -> service.authorize(params("client", "https://evil.example/cb", "S256", null, null), "1.1.1.1"))
                .isInstanceOf(OAuthException.class)
                .isNotInstanceOf(AuthorizationRedirectException.class);
        assertThatThrownBy(() -> service.authorize(params("client", null, "S256", null, null), "1.1.1.1"))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    void otherErrorsRedirectToClientWithStateAndIss() {
        assertThatThrownBy(() -> service.authorize(params("client", REDIRECT, "plain", null, null), "1.1.1.1"))
                .isInstanceOf(AuthorizationRedirectException.class)
                .extracting(e -> ((AuthorizationRedirectException) e).getRedirectUrl())
                .satisfies(u -> assertThat((String) u)
                        .startsWith(REDIRECT + "?error=invalid_request")
                        .contains("state=st%26ate")
                        .contains("iss=https%3A%2F%2Fpios.example"));

        assertThatThrownBy(() -> service.authorize(params("client", REDIRECT, "S256", "admin", null), "1.1.1.1"))
                .extracting(e -> ((AuthorizationRedirectException) e).getRedirectUrl())
                .satisfies(u -> assertThat((String) u).contains("error=invalid_scope"));

        assertThatThrownBy(() -> service.authorize(params("client", REDIRECT, "S256", null, "https://other/mcp"), "1.1.1.1"))
                .extracting(e -> ((AuthorizationRedirectException) e).getRedirectUrl())
                .satisfies(u -> assertThat((String) u).contains("error=invalid_target"));
    }

    @Test
    void approveIssuesHashedCodeAndConsumesRequest() {
        service.authorize(params("client", REDIRECT, "S256", "mcp:read", null), "1.1.1.1");
        String requestId = requests.keySet().iterator().next();

        String redirect = service.decide(requestId, 7L, true);

        assertThat(redirect).startsWith(REDIRECT + "?code=").contains("&state=st%26ate").contains("&iss=");
        ArgumentCaptor<OAuthAuthorizationCode> captor = ArgumentCaptor.forClass(OAuthAuthorizationCode.class);
        verify(codeRepo).save(captor.capture());
        String code = redirect.substring(redirect.indexOf("code=") + 5, redirect.indexOf('&'));
        assertThat(captor.getValue().getCodeHash()).isEqualTo(OAuthTokens.sha256Hex(code)).isNotEqualTo(code);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getScope()).isEqualTo("mcp:read");
        verify(requestRepo).delete(any());
    }

    @Test
    void denyRedirectsWithAccessDenied() {
        service.authorize(params("client", REDIRECT, "S256", null, null), "1.1.1.1");
        String requestId = requests.keySet().iterator().next();

        assertThat(service.decide(requestId, 7L, false)).startsWith(REDIRECT + "?error=access_denied");
        verify(codeRepo, never()).save(any());
    }

    @Test
    void expiredRequestIsRejected() {
        requests.put("old", OAuthAuthorizationRequest.builder().id("old").clientId("client").redirectUri(REDIRECT)
                .codeChallenge(CHALLENGE).scope("mcp:read").resource(RESOURCE)
                .expiresAt(Instant.now().minusSeconds(1)).build());
        assertThatThrownBy(() -> service.decide("old", 7L, true)).isInstanceOf(OAuthNotFoundException.class);
    }

    @Test
    void clientNeverAuthorizedGetsErrorPageInsteadOfRedirect() {
        when(clientService.resolve(eq("fresh"), any())).thenReturn(
                new ResolvedClient("fresh", "Fresh", List.of("https://attacker.example/landing"), false));
        when(grantRepo.existsByClientId("fresh")).thenReturn(false);

        assertThatThrownBy(() -> service.authorize(new OAuthAuthorizationService.AuthorizeParams("token", "fresh",
                "https://attacker.example/landing", CHALLENGE, "S256", "s", null, null), "1.1.1.1"))
                .isInstanceOf(OAuthException.class)
                .isNotInstanceOf(AuthorizationRedirectException.class)
                .extracting(e -> ((OAuthException) e).getError()).isEqualTo("unsupported_response_type");
    }

    @Test
    void clientWithGrantHistoryGetsErrorRedirect() {
        assertThatThrownBy(() -> service.authorize(new OAuthAuthorizationService.AuthorizeParams("token", "client",
                REDIRECT, CHALLENGE, "S256", "s", null, null), "1.1.1.1"))
                .isInstanceOf(AuthorizationRedirectException.class);
    }
}
