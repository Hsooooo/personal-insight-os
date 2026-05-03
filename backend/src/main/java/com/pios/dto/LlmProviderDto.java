package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LlmProviderDto {
    private Long id;
    private String providerName;
    private String defaultChatModel;
    private String embeddingModel;
    private Boolean enabled;
    private BigDecimal monthlyBudgetLimit;
}
