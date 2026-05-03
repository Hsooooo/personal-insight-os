package com.pios.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LlmProviderRequest {
    @NotBlank
    private String providerName;
    private String apiKey;
    private String defaultChatModel;
    private String embeddingModel;
    private Boolean enabled;
    private BigDecimal monthlyBudgetLimit;
}
