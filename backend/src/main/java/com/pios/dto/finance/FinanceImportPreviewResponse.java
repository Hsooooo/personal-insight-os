package com.pios.dto.finance;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceImportPreviewResponse {
    private String importSessionId;
    private int totalRows;
    private int newRows;
    private int duplicateRows;
    private int reviewRows;
    private List<FinanceImportRowDto> rows;
}
