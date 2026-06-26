package com.pios.dto.finance;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceCycleDto {
    private Long id;
    private String label;
    private LocalDate salaryDate;
    private Instant startsAt;
    private Instant endsAt;
    private String status;
}
