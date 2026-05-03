package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AskResponse {
    private Long questionId;
    private Long insightId;
    private String conclusion;
    private List<String> evidenceSummary;
    private List<String> relatedData;
    private String confidence;
    private String followUpQuestion;
}
