package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GraphRelationshipDto {
    private String id;
    private String type;
    private String sourceId;
    private String targetId;
    private BigDecimal confidence;
    private String sourceType;
    private Map<String, Object> properties;
}
