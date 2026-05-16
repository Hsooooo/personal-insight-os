package com.pios.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApiKeyRequest {

    @NotBlank
    private String name;
}
