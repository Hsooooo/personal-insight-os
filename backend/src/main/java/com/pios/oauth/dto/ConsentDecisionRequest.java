package com.pios.oauth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConsentDecisionRequest {
    @NotNull
    private Boolean approve;
}
