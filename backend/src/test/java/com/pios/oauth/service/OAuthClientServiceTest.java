package com.pios.oauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pios.oauth.OAuthException;
import com.pios.oauth.OAuthProperties;
import com.pios.oauth.domain.OAuthClient;
import com.pios.oauth.dto.ClientRegistrationRequest;
import com.pios.oauth.repository.OAuthClientRepository;
import com.pios.security.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OAuthClientServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private OAuthClientRepository repo;
    private ClientMetadataDocumentFetcher fetcher;
    private OAuthClientService service;

    @BeforeEach
    void setUp() {
        repo = mock(OAuthClientRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        fetcher = mock(ClientMetadataDocumentFetcher.class);
        service = new OAuthClientService(repo, fetcher, mapper,
                new OAuthProperties("https://pios.example", "https://pios.example/mcp", "https://pios.example"));
    }

    private static ClientRegistrationRequest req(List<String> redirects, String authMethod) {
        ClientRegistrationRequest r = new ClientRegistrationRequest();
        r.setClientName("My Client");
        r.setRedirectUris(redirects);
        r.setTokenEndpointAuthMethod(authMethod);
        return r;
    }

    @Test
    void registersPublicClient() {
        Map<String, Object> body = service.register(req(List.of("http://localhost:3333/cb"), null), "1.1.1.1");
        assertThat((String) body.get("client_id")).startsWith("mcp_");
        assertThat(body.get("token_endpoint_auth_method")).isEqualTo("none");
        assertThat(body.get("redirect_uris")).isEqualTo(List.of("http://localhost:3333/cb"));
        assertThat(body).containsKey("client_id_issued_at");
    }

    @Test
    void rejectsConfidentialClientsAndBadRedirects() {
        assertThatThrownBy(() -> service.register(req(List.of("https://a.example/cb"), "client_secret_basic"), "ip"))
                .isInstanceOf(OAuthException.class)
                .extracting(e -> ((OAuthException) e).getError()).isEqualTo("invalid_client_metadata");
        assertThatThrownBy(() -> service.register(req(List.of("http://evil.example/cb"), null), "ip"))
                .extracting(e -> ((OAuthException) e).getError()).isEqualTo("invalid_redirect_uri");
        assertThatThrownBy(() -> service.register(req(List.of(), null), "ip"))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    void registrationIsRateLimitedPerIp() {
        for (int i = 0; i < 20; i++) {
            service.register(req(List.of("https://a.example/cb"), null), "2.2.2.2");
        }
        assertThatThrownBy(() -> service.register(req(List.of("https://a.example/cb"), null), "2.2.2.2"))
                .isInstanceOf(TooManyRequestsException.class);
        service.register(req(List.of("https://a.example/cb"), null), "3.3.3.3");
    }

    @Test
    void resolvesRegisteredClient() {
        when(repo.findByClientId("mcp_x")).thenReturn(java.util.Optional.of(OAuthClient.builder()
                .clientId("mcp_x").clientName("X").redirectUris("[\"https://a.example/cb\"]").build()));
        assertThat(service.resolve("mcp_x", "1.1.1.1").redirectUris()).containsExactly("https://a.example/cb");
        assertThatThrownBy(() -> service.resolve("mcp_missing", "1.1.1.1")).isInstanceOf(OAuthException.class);
    }

    @Test
    void metadataDocumentMustMatchUrl() throws Exception {
        String url = "https://app.example.com/client.json";
        when(fetcher.fetch(url)).thenReturn(mapper.readTree(
                "{\"client_id\":\"https://evil.example.com/client.json\",\"redirect_uris\":[\"https://app.example.com/cb\"]}"));
        assertThatThrownBy(() -> service.resolve(url, "1.1.1.1")).isInstanceOf(OAuthException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void metadataDocumentResolvedAndCached() throws Exception {
        String url = "https://app.example.com/client.json";
        when(fetcher.fetch(url)).thenReturn(mapper.readTree("{\"client_id\":\"" + url
                + "\",\"client_name\":\"App\",\"redirect_uris\":[\"https://app.example.com/cb\"]}"));

        ResolvedClient c1 = service.resolve(url, "1.1.1.1");
        ResolvedClient c2 = service.resolve(url, "1.1.1.1");
        assertThat(c1.clientName()).isEqualTo("App");
        assertThat(c1.metadataDocument()).isTrue();
        assertThat(c2).isSameAs(c1);
        verify(fetcher, times(1)).fetch(url);
    }

    @Test
    void metadataDocumentFetchFailureIsInvalidClient() throws Exception {
        when(fetcher.fetch(anyString())).thenThrow(new IOException("client_id host resolves to a non-public address"));
        assertThatThrownBy(() -> service.resolve("https://internal.example.com/c.json", "1.1.1.1"))
                .isInstanceOf(OAuthException.class)
                .extracting(e -> ((OAuthException) e).getError()).isEqualTo("invalid_client");
    }

    @Test
    void metadataDocumentWithPrivateKeyJwtIsRejected() throws Exception {
        String url = "https://app.example.com/client.json";
        assertThatThrownBy(() -> OAuthClientService.parseMetadataDocument(url, mapper.readTree("{\"client_id\":\"" + url
                + "\",\"token_endpoint_auth_method\":\"private_key_jwt\",\"redirect_uris\":[\"https://a.example/cb\"]}")))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    void cimdFetchesAreRateLimitedPerIp() throws Exception {
        when(fetcher.fetch(anyString())).thenThrow(new IOException("boom"));
        for (int i = 0; i < 10; i++) {
            String url = "https://app" + i + ".example.com/c.json";
            assertThatThrownBy(() -> service.resolve(url, "4.4.4.4")).isInstanceOf(OAuthException.class);
        }
        assertThatThrownBy(() -> service.resolve("https://app99.example.com/c.json", "4.4.4.4"))
                .isInstanceOf(TooManyRequestsException.class);
        // cached failures don't count as fetches; other IPs unaffected
        assertThatThrownBy(() -> service.resolve("https://app0.example.com/c.json", "4.4.4.4"))
                .isInstanceOf(OAuthException.class);
        assertThatThrownBy(() -> service.resolve("https://app99.example.com/c.json", "5.5.5.5"))
                .isInstanceOf(OAuthException.class);
    }
}
