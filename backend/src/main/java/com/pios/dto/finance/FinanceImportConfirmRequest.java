package com.pios.dto.finance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceImportConfirmRequest {
    @Size(max = 100)
    private String importSessionId;
    @NotNull
    @Size(max = 20000)
    private List<FinanceImportDecisionDto> decisions;
}
