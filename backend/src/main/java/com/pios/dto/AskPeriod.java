package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskPeriod {
    private LocalDate start;
    private LocalDate end;
    private LocalDate baselineStart;
    private LocalDate baselineEnd;
}
