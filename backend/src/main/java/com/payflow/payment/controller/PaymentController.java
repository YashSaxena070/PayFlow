package com.payflow.payment.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentAttemptResponse;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    private UUID getMerchantId() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Authenticated merchant context is required");
        }
        return merchantId;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        UUID merchantId = getMerchantId();
        PaymentResponse response = paymentService.createPayment(merchantId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, CorrelationIdContext.get()));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID paymentId) {
        UUID merchantId = getMerchantId();
        PaymentResponse response = paymentService.getPayment(merchantId, paymentId);
        return ResponseEntity.ok(ApiResponse.of(response, CorrelationIdContext.get()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        UUID merchantId = getMerchantId();
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sortBy));
        Page<PaymentResponse> payments = paymentService.searchPayments(
                merchantId, status, currency, orderId, fromDate, toDate, pageRequest);
        return ResponseEntity.ok(ApiResponse.of(payments, CorrelationIdContext.get()));
    }

    @GetMapping("/{paymentId}/attempts")
    public ResponseEntity<ApiResponse<List<PaymentAttemptResponse>>> getAttempts(@PathVariable UUID paymentId) {
        UUID merchantId = getMerchantId();
        List<PaymentAttemptResponse> attempts = paymentService.getAttempts(merchantId, paymentId);
        return ResponseEntity.ok(ApiResponse.of(attempts, CorrelationIdContext.get()));
    }

    @PostMapping("/{paymentId}/sync")
    public ResponseEntity<ApiResponse<PaymentResponse>> syncPayment(@PathVariable UUID paymentId) {
        UUID merchantId = getMerchantId();
        PaymentResponse response = paymentService.syncPaymentStatus(merchantId, paymentId);
        return ResponseEntity.ok(ApiResponse.of(response, CorrelationIdContext.get()));
    }

    @PostMapping("/{paymentId}/failover")
    public ResponseEntity<ApiResponse<PaymentResponse>> failoverPayment(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        UUID merchantId = getMerchantId();
        String reason = body != null ? body.get("reason") : null;
        PaymentResponse response = paymentService.failoverPayment(merchantId, paymentId, reason);
        return ResponseEntity.ok(ApiResponse.of(response, CorrelationIdContext.get()));
    }
}

