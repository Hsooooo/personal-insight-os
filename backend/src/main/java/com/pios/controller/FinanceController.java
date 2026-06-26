package com.pios.controller;

import com.pios.dto.ApiResponse;
import com.pios.dto.finance.*;
import com.pios.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceService financeService;

    @GetMapping("/cycles")
    public ApiResponse<List<FinanceCycleDto>> cycles(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(financeService.getCycles(userId));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<FinanceTransactionDto>> transactions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cycleId) {
        return ApiResponse.ok(financeService.getTransactions(userId, cycleId));
    }

    @PostMapping("/import/preview")
    public ApiResponse<FinanceImportPreviewResponse> preview(
            @AuthenticationPrincipal Long userId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(financeService.previewImport(userId, file));
    }

    @PostMapping("/import/confirm")
    public ApiResponse<FinanceImportConfirmResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @RequestBody FinanceImportConfirmRequest request) {
        return ApiResponse.ok(financeService.confirmImport(userId, request));
    }

    @GetMapping("/recurring-bills")
    public ApiResponse<List<RecurringBillDto>> recurringBills(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(financeService.getRecurringBills(userId));
    }

    @PostMapping("/recurring-bills")
    public ApiResponse<RecurringBillDto> createRecurringBill(
            @AuthenticationPrincipal Long userId,
            @RequestBody RecurringBillDto dto) {
        return ApiResponse.ok(financeService.createRecurringBill(userId, dto));
    }

    @PostMapping("/recurring-bills/{templateId}/versions")
    public ApiResponse<RecurringBillVersionDto> createRecurringBillVersion(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId,
            @RequestBody RecurringBillVersionDto dto) {
        return ApiResponse.ok(financeService.createRecurringBillVersion(userId, templateId, dto));
    }

    @DeleteMapping("/recurring-bills/{templateId}")
    public ApiResponse<Void> deleteRecurringBill(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long templateId) {
        financeService.deleteRecurringBill(userId, templateId);
        return ApiResponse.ok("Deleted", null);
    }
}
