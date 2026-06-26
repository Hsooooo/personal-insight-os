package com.pios.dto.finance;

import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecurringBillItemDto {
    private Long id;
    private String itemName;
    private BigDecimal amount;
    private String itemType;
    private Integer sortOrder;
}
