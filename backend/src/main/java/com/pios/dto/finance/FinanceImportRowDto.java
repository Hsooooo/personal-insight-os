package com.pios.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceImportRowDto {
    private String rowId;
    private int rowNumber;
    private String status;
    private Long matchedTransactionId;
    private Long cycleId;
    private String cycleLabel;
    private Instant cycleStartsAt;
    private LocalDate cycleSalaryDate;
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
    private boolean cashflowIncluded;
    private boolean spendingIncluded;
    private BigDecimal cashflowAmount;
    private BigDecimal spendingAmount;
    private String paymentMethod;
    private Map<String, Object> sourceRow;
}
