package com.pios.service;

import com.pios.domain.LlmProvider;
import com.pios.domain.User;
import com.pios.dto.LlmProviderDto;
import com.pios.dto.LlmProviderRequest;
import com.pios.repository.LlmProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmProviderService {

    private final LlmProviderRepository llmRepo;

    public List<LlmProviderDto> getProviders(Long userId) {
        return llmRepo.findByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public LlmProviderDto createProvider(Long userId, LlmProviderRequest request) {
        LlmProvider provider = LlmProvider.builder()
                .user(User.builder().id(userId).build())
                .providerName(request.getProviderName())
                .apiKeyEncrypted(request.getApiKey())
                .defaultChatModel(request.getDefaultChatModel())
                .embeddingModel(request.getEmbeddingModel())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .monthlyBudgetLimit(request.getMonthlyBudgetLimit())
                .build();
        return toDto(llmRepo.save(provider));
    }

    @Transactional
    public LlmProviderDto updateProvider(Long userId, Long providerId, LlmProviderRequest request) {
        var provider = llmRepo.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        if (!provider.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
        provider.setProviderName(request.getProviderName());
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            provider.setApiKeyEncrypted(request.getApiKey());
        }
        provider.setDefaultChatModel(request.getDefaultChatModel());
        provider.setEmbeddingModel(request.getEmbeddingModel());
        provider.setEnabled(request.getEnabled());
        provider.setMonthlyBudgetLimit(request.getMonthlyBudgetLimit());
        return toDto(llmRepo.save(provider));
    }

    @Transactional
    public void deleteProvider(Long userId, Long providerId) {
        var provider = llmRepo.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        if (!provider.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }
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
