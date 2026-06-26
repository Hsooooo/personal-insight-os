package com.pios.dto.finance;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceImportDecisionDto {
    private String action;
    private FinanceImportRowDto row;
}
