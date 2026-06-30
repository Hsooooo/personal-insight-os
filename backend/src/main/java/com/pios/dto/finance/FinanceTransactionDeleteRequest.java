package com.pios.dto.finance;

import lombok.Data;

import java.util.List;

@Data
public class FinanceTransactionDeleteRequest {
    private List<Long> transactionIds;
}
