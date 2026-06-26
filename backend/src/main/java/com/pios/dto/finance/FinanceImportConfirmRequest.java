package com.pios.dto.finance;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceImportConfirmRequest {
    private String importSessionId;
    private List<FinanceImportDecisionDto> decisions;
}
