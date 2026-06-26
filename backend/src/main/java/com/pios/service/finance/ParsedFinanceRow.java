package com.pios.service.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record ParsedFinanceRow(
        int rowNumber,
        Instant transactionAt,
        LocalDate transactionDate,
        String asset,
        String category,
        String subcategory,
        String description,
        BigDecimal amount,
        String flowType,
        String memo,
        String currency,
        String sourceFingerprint,
        boolean cashflowIncluded,
        boolean spendingIncluded,
        String paymentMethod,
        Map<String, Object> sourceRow
) {
}
