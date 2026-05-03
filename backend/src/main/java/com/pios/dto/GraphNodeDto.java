package com.pios.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GraphNodeDto {
    private String id;
    private String type;
    private String label;
    private Map<String, Object> properties;
}
