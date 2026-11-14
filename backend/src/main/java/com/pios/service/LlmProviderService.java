package com.pios.service;

import com.pios.common.NotFoundException;
import com.pios.domain.LlmProvider;
import com.pios.domain.User;
import com.pios.dto.LlmProviderDto;
import com.pios.dto.LlmProviderRequest;
import com.pios.repository.LlmProviderRepository;
import com.pios.security.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LlmProviderService {

    private final LlmProviderRepository llmRepo;
    private final SecretCryptoService secretCrypto;

    public List<LlmProviderDto> getProviders(Long userId) {
        return llmRepo.findByUserId(userId).stream().map(this::toDto).toList();
    }

    /** 활성화된 사용자 Provider (복호화된 키 포함). API 키는 로그/응답에 노출하지 말 것. */
    public record ResolvedProvider(String providerName, String apiKey, String chatModel, String embeddingModel) {
        @Override
        public String toString() {
            return "ResolvedProvider[providerName=" + providerName + ", chatModel=" + chatModel
                    + ", embeddingModel=" + embeddingModel + "]";
        }
    }

    /**
     * 사용자의 활성화된 첫 번째 OpenAI Provider를 복호화해 반환한다. 평문으로 남아있던 키는 이때 암호화 저장한다.
     * LlmClient/EmbeddingService는 OpenAI 호환 엔드포인트만 호출하므로, 다른 Provider(Anthropic, Google 등)의 키는
     * 절대 반환하지 않는다 — 다른 회사 키를 OpenAI로 전송하는 것을 막기 위함.
     */
    @Transactional
    public Optional<ResolvedProvider> resolveEnabledProvider(Long userId) {
        return llmRepo.findByUserId(userId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .filter(p -> isOpenAiCompatible(p.getProviderName()))
                .min(Comparator.comparing(LlmProvider::getId))
                .map(p -> new ResolvedProvider(
                        p.getProviderName(), migrateAndDecrypt(p), p.getDefaultChatModel(), p.getEmbeddingModel()));
    }

    static boolean isOpenAiCompatible(String providerName) {
        return providerName == null || providerName.isBlank() || providerName.trim().equalsIgnoreCase("OpenAI");
    }

    /**
     * Returns decrypted API key for an enabled provider, or null if none.
     */
    @Transactional
    public String resolveDecryptedApiKey(Long userId) {
        return resolveEnabledProvider(userId).map(ResolvedProvider::apiKey).orElse(null);
    }

    @Transactional
    public LlmProviderDto createProvider(Long userId, LlmProviderRequest request) {
        LlmProvider provider = LlmProvider.builder()
                .user(User.builder().id(userId).build())
                .providerName(request.getProviderName())
                .apiKeyEncrypted(secretCrypto.encrypt(request.getApiKey()))
                .defaultChatModel(request.getDefaultChatModel())
                .embeddingModel(request.getEmbeddingModel())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .monthlyBudgetLimit(request.getMonthlyBudgetLimit())
                .build();
        return toDto(llmRepo.save(provider));
    }

    @Transactional
    public LlmProviderDto updateProvider(Long userId, Long providerId, LlmProviderRequest request) {
        var provider = llmRepo.findByIdAndUserId(providerId, userId)
                .orElseThrow(() -> new NotFoundException("Provider not found"));
        provider.setProviderName(request.getProviderName());
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            provider.setApiKeyEncrypted(secretCrypto.encrypt(request.getApiKey()));
        }
        provider.setDefaultChatModel(request.getDefaultChatModel());
        provider.setEmbeddingModel(request.getEmbeddingModel());
        provider.setEnabled(request.getEnabled());
        provider.setMonthlyBudgetLimit(request.getMonthlyBudgetLimit());
        return toDto(llmRepo.save(provider));
    }

    private String migrateAndDecrypt(LlmProvider provider) {
        String stored = provider.getApiKeyEncrypted();
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!secretCrypto.isEncrypted(stored)) {
            provider.setApiKeyEncrypted(secretCrypto.encrypt(stored));
            llmRepo.save(provider);
            return stored;
        }
        return secretCrypto.decrypt(stored);
    }

    @Transactional
    public void deleteProvider(Long userId, Long providerId) {
        var provider = llmRepo.findByIdAndUserId(providerId, userId)
                .orElseThrow(() -> new NotFoundException("Provider not found"));
        llmRepo.delete(provider);
    }

    private LlmProviderDto toDto(LlmProvider p) {
        return LlmProviderDto.builder()
                .id(p.getId()).providerName(p.getProviderName())
                .defaultChatModel(p.getDefaultChatModel())
                .embeddingModel(p.getEmbeddingModel())
                .enabled(p.getEnabled())
                .monthlyBudgetLimit(p.getMonthlyBudgetLimit())
                .build();
    }
}
