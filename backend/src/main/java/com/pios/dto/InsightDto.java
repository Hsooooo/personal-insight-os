package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InsightDto {
    private Long id;
    private String category;
    private String title;
    private String summary;
    private BigDecimal confidence;
    private String modelProvider;
    private String modelName;
    private String feedbackStatus;
    private Boolean isSaved;
    private Instant createdAt;
    private List<EvidenceDto> evidences;
}
