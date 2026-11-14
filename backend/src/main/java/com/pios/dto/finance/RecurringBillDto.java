package com.pios.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecurringBillDto {
    private Long id;
    @NotBlank
    @Size(max = 100)
    private String name;
    private String provider;
    private String category;
    @Size(max = 1000)
    private String memo;
    private boolean active;
    private List<RecurringBillVersionDto> versions;
}
