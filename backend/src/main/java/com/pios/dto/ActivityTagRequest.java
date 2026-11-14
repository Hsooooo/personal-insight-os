package com.pios.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTagRequest {
    /** null 또는 공백이면 태그 제거 */
    @Size(max = 100)
    private String userTag;
}
