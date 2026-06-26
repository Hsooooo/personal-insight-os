package com.pios.dto.finance;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceImportConfirmResponse {
    private int created;
    private int skipped;
    private List<FinanceTransactionDto> transactions;
}
