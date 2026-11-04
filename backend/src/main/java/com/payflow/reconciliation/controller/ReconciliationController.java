package com.payflow.reconciliation.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.reconciliation.domain.ReconciliationCase;
import com.payflow.reconciliation.service.ReconciliationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation-cases")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReconciliationCase>>> listCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ReconciliationCase> cases = reconciliationService.listCases(PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(ApiResponse.of(cases, CorrelationIdContext.get()));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ApiResponse<ReconciliationCase>> resolveCase(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        String resolution = request.getOrDefault("resolution", "MARK_FAILED");
        String reason = request.getOrDefault("reason", "Operator manual resolution");
        ReconciliationCase resolved = reconciliationService.resolveCase(id, resolution, reason);
        return ResponseEntity.ok(ApiResponse.of(resolved, CorrelationIdContext.get()));
    }

    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<List<ReconciliationCase>>> triggerScan(
            @RequestParam(defaultValue = "300") int staleSeconds) {
        List<ReconciliationCase> cases = reconciliationService.scanStaleAttempts(staleSeconds);
        return ResponseEntity.ok(ApiResponse.of(cases, CorrelationIdContext.get()));
    }
}

