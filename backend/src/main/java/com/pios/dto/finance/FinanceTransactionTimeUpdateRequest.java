package com.pios.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceTransactionTimeUpdateRequest {
    @NotBlank
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "must use HH:mm format")
    private String time;
}
