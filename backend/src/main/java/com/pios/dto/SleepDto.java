package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SleepDto {
    private Long id;
    private LocalDate sleepDate;
    private Instant startTime;
    private Instant endTime;
    private Integer totalSleepSeconds;
    private Integer deepSleepSeconds;
    private Integer lightSleepSeconds;
    private Integer remSleepSeconds;
    private Integer awakeSeconds;
    private Integer sleepScore;
}
