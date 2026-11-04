package com.payflow.refund.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.refund.dto.CreateRefundRequest;
import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    private UUID getMerchantId() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }
        return merchantId;
    }

    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> createRefund(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {
        UUID merchantId = getMerchantId();
        RefundResponse response = refundService.createRefund(merchantId, paymentId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, CorrelationIdContext.get()));
    }

    @GetMapping("/refunds/{refundId}")
    public ResponseEntity<ApiResponse<RefundResponse>> getRefund(@PathVariable UUID refundId) {
        UUID merchantId = getMerchantId();
        RefundResponse response = refundService.getRefund(merchantId, refundId);
        return ResponseEntity.ok(ApiResponse.of(response, CorrelationIdContext.get()));
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<Page<RefundResponse>>> listRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID merchantId = getMerchantId();
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100));
        Page<RefundResponse> refunds = refundService.listRefunds(merchantId, pageRequest);
        return ResponseEntity.ok(ApiResponse.of(refunds, CorrelationIdContext.get()));
    }
}

