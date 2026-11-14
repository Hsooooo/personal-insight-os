package com.pios.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotBlank
    @Pattern(regexp = "CORRECT|UNCLEAR|WRONG|IMPORTANT", message = "must be one of CORRECT, UNCLEAR, WRONG, IMPORTANT")
    private String feedbackStatus;
}
