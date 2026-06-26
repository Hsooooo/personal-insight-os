package com.pios.dto.finance;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceTransactionTimeUpdateRequest {
    private String time;
}
