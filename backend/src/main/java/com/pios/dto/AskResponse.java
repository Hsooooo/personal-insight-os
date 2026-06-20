package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskResponse {
    private Long questionId;
    private Long insightId;
    private String answer;
    private String intent;
    private AskPeriod period;
    private AskConfidence confidence;
    private List<AskEvidence> evidences;
    private List<String> followUpQuestions;
}
