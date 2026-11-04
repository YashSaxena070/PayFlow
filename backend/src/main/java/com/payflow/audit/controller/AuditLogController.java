package com.payflow.audit.controller;

import com.payflow.audit.domain.AuditLog;
import com.payflow.audit.service.AuditLogService;
import com.payflow.common.dto.ApiResponse;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID merchantId = TenantContext.getMerchantId();
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        Page<AuditLog> logs = auditLogService.listLogs(merchantId, pageRequest);
        return ResponseEntity.ok(ApiResponse.of(logs, CorrelationIdContext.get()));
    }
}

