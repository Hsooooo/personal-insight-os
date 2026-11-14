package com.pios.service.llm;

import com.pios.service.LlmProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmClientTest {

    private RestTemplate restTemplate;
    private LlmProviderService providerService;
    private LlmProperties properties;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        providerService = mock(LlmProviderService.class);
        properties = new LlmProperties();
        properties.setBaseUrl("https://llm.example.com/v1/");
        properties.setModel("global-model");
        client = new LlmClient(restTemplate, new LlmCredentialsResolver(providerService, properties), properties);
    }

    private static Map<String, Object> ok(String content) {
        return Map.of("choices", List.of(Map.of("message", Map.of("content", content))));
    }

    @SuppressWarnings("unchecked")
    private HttpEntity<Map<String, Object>> captureRequest() {
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("https://llm.example.com/v1/chat/completions"), captor.capture(), eq(Map.class));
        return captor.getValue();
    }

    @Test
    void userProviderKeyAndModelTakePrecedence() {
        properties.setApiKey("global-key");
        when(providerService.resolveEnabledProvider(1L)).thenReturn(Optional.of(
                new LlmProviderService.ResolvedProvider("OpenAI", "user-key", "user-model", null)));
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(ok(" answer "));

        Optional<LlmClient.ChatResult> result = client.chat(1L, LlmPurpose.ASK, "sys", "user");

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo("answer");
        assertThat(result.get().model()).isEqualTo("user-model");
        HttpEntity<Map<String, Object>> request = captureRequest();
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer user-key");
        assertThat(request.getBody()).containsEntry("model", "user-model").containsEntry("max_tokens", 500);
    }

    @Test
    void fallsBackToGlobalKeyWhenUserHasNoProvider() {
        properties.setApiKey("global-key");
        LlmProperties.Purpose p = new LlmProperties.Purpose();
        p.setMaxTokens(42);
        properties.getPurposes().put("anomaly-alert", p);
        when(providerService.resolveEnabledProvider(1L)).thenReturn(Optional.empty());
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(ok("x"));

        Optional<LlmClient.ChatResult> result = client.chat(1L, LlmPurpose.ANOMALY_ALERT, "sys", "user");

        assertThat(result).isPresent();
        HttpEntity<Map<String, Object>> request = captureRequest();
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer global-key");
        assertThat(request.getBody()).containsEntry("model", "global-model").containsEntry("max_tokens", 42)
                .containsEntry("temperature", 0.4);
    }

    @Test
    void userProviderResolutionFailureFallsBackToGlobal() {
        properties.setApiKey("global-key");
        when(providerService.resolveEnabledProvider(1L)).thenThrow(new IllegalStateException("decrypt failed"));
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(ok("x"));

        assertThat(client.chat(1L, LlmPurpose.ASK, "s", "u")).isPresent();
        assertThat(captureRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer global-key");
    }

    @Test
    void noCredentialsMeansNoHttpCall() {
        when(providerService.resolveEnabledProvider(1L)).thenReturn(Optional.empty());

        assertThat(client.chat(1L, LlmPurpose.ASK, "s", "u")).isEmpty();
        assertThat(client.isAvailable(1L)).isFalse();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void httpErrorsAndTimeoutsReturnEmpty() {
        properties.setApiKey("global-key");
        when(providerService.resolveEnabledProvider(anyLong())).thenReturn(Optional.empty());
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
                .thenThrow(new ResourceAccessException("Read timed out"))
                .thenReturn(Map.of("choices", List.of()));

        assertThat(client.chat(1L, LlmPurpose.ASK, "s", "u")).isEmpty();
        assertThat(client.chat(1L, LlmPurpose.ASK, "s", "u")).isEmpty();
        assertThat(client.chat(1L, LlmPurpose.ASK, "s", "u")).isEmpty();
    }

    @Test
    void credentialsToStringDoesNotLeakKey() {
        LlmCredentials c = new LlmCredentials("sk-secret", "OpenAI", "m", "e", LlmCredentials.Source.USER);
        assertThat(c.toString()).doesNotContain("sk-secret");
    }
}
