package com.payflow.refund.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.service.IdempotencyService;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.refund.domain.Refund;
import com.payflow.refund.domain.RefundStatus;
import com.payflow.refund.dto.CreateRefundRequest;
import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderRegistry providerRegistry;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public RefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            ProviderRegistry providerRegistry,
            IdempotencyService idempotencyService,
            OutboxService outboxService,
            ObjectMapper objectMapper) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.providerRegistry = providerRegistry;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RefundResponse createRefund(UUID merchantId, UUID paymentId, String idempotencyKey, CreateRefundRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Idempotency-Key header is required for refunds");
        }

        String canonical = String.format("%s:%d:%s", paymentId, request.amountMinor(), request.reason() != null ? request.reason() : "");
        String requestHash = IdempotencyService.computeHash(canonical);

        // 1. Check Idempotency Store
        Optional<IdempotencyRecord> cachedOpt = idempotencyService.checkIdempotency(
                merchantId, "CREATE_REFUND", idempotencyKey, requestHash);

        if (cachedOpt.isPresent()) {
            try {
                return objectMapper.readValue(cachedOpt.get().getResponseBodySafe(), RefundResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached refund response", e);
                return getRefund(merchantId, cachedOpt.get().getResourceId());
            }
        }

        // 2. Fetch Payment and Validate Status
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));

        if (!payment.getStatus().canRefund()) {
            throw new PayflowException(ErrorCode.PAYMENT_NOT_REFUNDABLE,
                    "Payment cannot be refunded in current status: " + payment.getStatus());
        }

        // 3. Validate Cumulative Balance Invariant (sum <= captured_amount)
        Long activeRefunded = refundRepository.sumActiveRefundAmountByPaymentId(paymentId);
        if (activeRefunded == null) activeRefunded = 0L;

        long availableToRefund = payment.getAmountMinor() - activeRefunded;
        if (request.amountMinor() > availableToRefund) {
            throw new PayflowException(ErrorCode.REFUND_EXCEEDS_PAYMENT_AMOUNT,
                    String.format("Refund amount (%d) exceeds available balance (%d)", request.amountMinor(), availableToRefund));
        }

        // 4. Find successful attempt for provider details
        PaymentAttempt successfulAttempt = payment.getAttempts().stream()
                .filter(a -> a.getStatus() == AttemptStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow(() -> new PayflowException(ErrorCode.PAYMENT_NOT_REFUNDABLE, "No successful attempt found to refund"));

        // 5. Create Refund aggregate in PENDING status
        Refund refund = new Refund(
                UUID.randomUUID(),
                payment,
                payment.getMerchant(),
                request.amountMinor(),
                payment.getCurrency(),
                idempotencyKey,
                request.reason()
        );
        refund = refundRepository.save(refund);

        // 6. Dispatch to Provider Adapter
        PaymentProviderPort adapter = providerRegistry.getProvider(successfulAttempt.getProvider().getCode());
        PaymentProviderPort.ProviderRefundCommand command = new PaymentProviderPort.ProviderRefundCommand(
                successfulAttempt.getProviderReference(),
                refund.getId(),
                refund.getAmountMinor(),
                refund.getCurrency(),
                refund.getReason(),
                null
        );

        PaymentProviderPort.ProviderRefundResult result = adapter.refund(command);

        // 7. Update Refund Status
        if (result.success()) {
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setProviderRefundReference(result.providerRefundReference());
            refund = refundRepository.save(refund);

            // Update Payment Status
            long totalRefunded = activeRefunded + refund.getAmountMinor();
            if (totalRefunded >= payment.getAmountMinor()) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }
            paymentRepository.save(payment);
        } else {
            refund.setStatus(RefundStatus.FAILED);
            refund = refundRepository.save(refund);
        }

        // 8. Atomic Outbox Event
        outboxService.recordEvent(
                "Refund",
                refund.getId(),
                "REFUND_COMPLETED",
                Map.of(
                        "refundId", refund.getId().toString(),
                        "paymentId", payment.getId().toString(),
                        "status", refund.getStatus().name(),
                        "amountMinor", refund.getAmountMinor(),
                        "currency", refund.getCurrency()
                )
        );

        // 9. Cache in Idempotency Store
        RefundResponse response = RefundResponse.from(refund);
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.recordSuccess(
                    merchantId,
                    "CREATE_REFUND",
                    idempotencyKey,
                    requestHash,
                    refund.getId(),
                    200,
                    responseJson
            );
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize refund response for idempotency cache", e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefund(UUID merchantId, UUID refundId) {
        Refund refund = refundRepository.findByIdAndMerchantId(refundId, merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Refund not found: " + refundId));
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public Page<RefundResponse> listRefunds(UUID merchantId, Pageable pageable) {
        return refundRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                .map(RefundResponse::from);
    }
}

