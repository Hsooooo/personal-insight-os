package com.pios.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LlmProviderRequest {
    @NotBlank
    @Size(max = 50)
    private String providerName;
    @Size(max = 500)
    private String apiKey;
    @Size(max = 100)
    private String defaultChatModel;
    @Size(max = 100)
    private String embeddingModel;
    private Boolean enabled;
    @PositiveOrZero
    @Digits(integer = 10, fraction = 2)
    private BigDecimal monthlyBudgetLimit;
}
