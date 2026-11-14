package com.pios.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceAccountDto {
    private Long id;
    @NotBlank
    @Size(max = 100)
    private String name;
    private String accountType;
    private String role;
    private String institution;
    @Size(max = 1000)
    private String memo;
    private Boolean active;
    private BigDecimal openingBalance;
    private LocalDate openingBalanceDate;
    private String openingBalanceMemo;
    @Size(max = 50)
    private List<@NotBlank @Size(max = 100) String> aliases;
    /** Seed openingBalance + net of cashflow before the selected cycle/period start. */
    private BigDecimal periodOpeningBalance;
    private BigDecimal cycleIncome;
    private BigDecimal cycleCashOut;
    private BigDecimal cycleNetFlow;
    /** periodOpeningBalance + cycleNetFlow (closing balance for the selected period). */
    private BigDecimal estimatedBalance;
}
