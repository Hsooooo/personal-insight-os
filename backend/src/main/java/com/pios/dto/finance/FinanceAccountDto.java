package com.pios.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceAccountDto {
    private Long id;
    private String name;
    private String accountType;
    private String role;
    private String institution;
    private String memo;
    private Boolean active;
    private List<String> aliases;
    private BigDecimal cycleIncome;
    private BigDecimal cycleCashOut;
    private BigDecimal cycleNetFlow;
}
