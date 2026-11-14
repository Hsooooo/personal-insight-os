package com.pios.service.llm;

import com.pios.service.LlmProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 사용자 LLM Provider(활성화된 첫 번째, 복호화된 키) → 전역 OPENAI_API_KEY 순으로 자격증명을 결정한다.
 * Chat(LlmClient)과 Embedding(EmbeddingService)이 같은 규칙을 공유한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmCredentialsResolver {

    private final LlmProviderService llmProviderService;
    private final LlmProperties properties;

    public Optional<LlmCredentials> resolve(Long userId) {
        if (userId != null) {
            try {
                Optional<LlmProviderService.ResolvedProvider> user = llmProviderService.resolveEnabledProvider(userId);
                if (user.isPresent() && notBlank(user.get().apiKey())) {
                    LlmProviderService.ResolvedProvider p = user.get();
                    return Optional.of(new LlmCredentials(
                            p.apiKey(),
                            notBlank(p.providerName()) ? p.providerName() : "OpenAI",
                            notBlank(p.chatModel()) ? p.chatModel() : properties.getModel(),
                            notBlank(p.embeddingModel()) ? p.embeddingModel() : properties.getEmbeddingModel(),
                            LlmCredentials.Source.USER));
                }
            } catch (Exception e) {
                // 사용자 키 복호화 실패 등: 전역 키로 fallback 하되 원인은 남긴다
                log.warn("Failed to resolve user LLM provider for user {}: {}", userId, e.getMessage());
            }
        }
        if (notBlank(properties.getApiKey())) {
            return Optional.of(new LlmCredentials(
                    properties.getApiKey(), "OpenAI", properties.getModel(), properties.getEmbeddingModel(),
                    LlmCredentials.Source.GLOBAL));
        }
        return Optional.empty();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
