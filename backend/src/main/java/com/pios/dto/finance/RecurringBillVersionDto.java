package com.pios.dto.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
    @Valid
    @Size(max = 100)
    private List<RecurringBillItemDto> items;
}
