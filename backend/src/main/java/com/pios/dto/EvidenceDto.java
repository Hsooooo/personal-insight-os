package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EvidenceDto {
    private Long id;
    private String evidenceType;
    private String sourceTable;
    private Long sourceId;
    private String evidenceSummary;
    private BigDecimal weight;
}
