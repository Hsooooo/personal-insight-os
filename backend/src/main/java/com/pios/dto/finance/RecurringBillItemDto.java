package com.pios.dto.finance;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecurringBillItemDto {
    private Long id;
    @Size(max = 100)
    private String itemName;
    private BigDecimal amount;
    private String itemType;
    private Integer sortOrder;
}
