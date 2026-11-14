package com.pios.dto.finance;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FinanceTransactionDeleteRequest {
    @NotEmpty
    @Size(max = 5000)
    private List<Long> transactionIds;
}
