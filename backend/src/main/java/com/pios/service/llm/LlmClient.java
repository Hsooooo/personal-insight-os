package com.pios.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI 호환 Chat Completions 단일 진입점.
 * <ul>
 *   <li>사용자 LLM Provider 키 → 전역 키 순으로 자격증명 결정 ({@link LlmCredentialsResolver})</li>
 *   <li>모델/temperature/max_tokens/base URL/타임아웃은 설정으로 제어</li>
 *   <li>실패는 로그로 남기고 {@link Optional#empty()}를 반환해 호출 측이 fallback 하도록 한다</li>
 * </ul>
 * 호출 측은 DB 트랜잭션 밖에서 호출해야 한다 (HTTP 대기 동안 커넥션 점유 방지).
 */
@Slf4j
@Component
public class LlmClient {

    private final RestTemplate restTemplate;
    private final LlmCredentialsResolver credentialsResolver;
    private final LlmProperties properties;

    public LlmClient(@Qualifier("llmRestTemplate") RestTemplate restTemplate,
                     LlmCredentialsResolver credentialsResolver,
                     LlmProperties properties) {
        this.restTemplate = restTemplate;
        this.credentialsResolver = credentialsResolver;
        this.properties = properties;
    }

    public record ChatResult(String content, String provider, String model) {}

    /** 사용자에게 사용 가능한 LLM 자격증명이 있는지 (없으면 호출 측은 바로 fallback) */
    public boolean isAvailable(Long userId) {
        return credentialsResolver.resolve(userId).isPresent();
    }

    /** fallback 응답 시 Insight.modelName에 기록할 기본 모델명 */
    public String defaultModel() {
        return properties.getModel();
    }

    public Optional<ChatResult> chat(Long userId, LlmPurpose purpose, String systemPrompt, String userPrompt) {
        Optional<LlmCredentials> credentials = credentialsResolver.resolve(userId);
        if (credentials.isEmpty()) {
            log.debug("No LLM credentials for user {} ({}); using fallback", userId, purpose);
            return Optional.empty();
        }
        LlmCredentials creds = credentials.get();
        String url = properties.normalizedBaseUrl() + "/chat/completions";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(creds.apiKey());

            Map<String, Object> body = Map.of(
                    "model", creds.chatModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                            Map.of("role", "user", "content", userPrompt != null ? userPrompt : "")
                    ),
                    "max_tokens", properties.maxTokensFor(purpose),
                    "temperature", properties.temperatureFor(purpose)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                log.warn("LLM returned empty content (user={}, purpose={}, model={}, source={})",
                        userId, purpose, creds.chatModel(), creds.source());
                return Optional.empty();
            }
            return Optional.of(new ChatResult(content.trim(), creds.providerName(), creds.chatModel()));
        } catch (RestClientResponseException e) {
            log.warn("LLM request failed (user={}, purpose={}, model={}, source={}): HTTP {} {}",
                    userId, purpose, creds.chatModel(), creds.source(), e.getStatusCode().value(),
                    truncate(e.getResponseBodyAsString(), 300));
        } catch (Exception e) {
            log.warn("LLM request failed (user={}, purpose={}, model={}, source={}): {}",
                    userId, purpose, creds.chatModel(), creds.source(), e.toString());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) return null;
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) return null;
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) return null;
        Object messageObj = choice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) return null;
        Object content = message.get("content");
        return content != null ? content.toString() : null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
