package com.pios.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecurringBillVersionDto {
    private Long id;
    private Long templateId;
    private Long effectiveCycleId;
    private Integer version;
    private BigDecimal expectedAmount;
    private boolean active;
    private List<RecurringBillItemDto> items;
}
