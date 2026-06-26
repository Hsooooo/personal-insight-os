package com.pios.dto.finance;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecurringBillDto {
    private Long id;
    private String name;
    private String provider;
    private String category;
    private String memo;
    private boolean active;
    private List<RecurringBillVersionDto> versions;
}
