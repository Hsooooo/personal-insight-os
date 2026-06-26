package com.pios.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceTransactionDto {
    private Long id;
    private Long cycleId;
    private Long linkedTemplateVersionId;
    private Instant transactionAt;
    private LocalDate transactionDate;
    private String asset;
    private String category;
    private String subcategory;
    private String description;
    private BigDecimal amount;
    private String flowType;
    private String memo;
    private String currency;
    private String sourceFingerprint;
    private Map<String, Object> sourceRow;
    private boolean cashflowIncluded;
    private boolean spendingIncluded;
    private String paymentMethod;
}
