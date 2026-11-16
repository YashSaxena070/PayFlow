package com.payflow.payment.dto;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID merchantId,
        String orderId,
        Long amountMinor,
        String currency,
        String paymentMethod,
        PaymentStatus status,
        String idempotencyKey,
        List<PaymentAttemptResponse> attempts,
        String redirectUrl,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return from(payment, null, Map.of());
    }

    public static PaymentResponse from(Payment payment, String redirectUrl) {
        return from(payment, redirectUrl, Map.of());
    }

    public static PaymentResponse from(Payment payment, String redirectUrl, Map<String, Object> metadata) {
        List<PaymentAttemptResponse> attemptDtos = payment.getAttempts() != null
                ? payment.getAttempts().stream().map(PaymentAttemptResponse::from).toList()
                : List.of();

        return new PaymentResponse(
                payment.getId(),
                payment.getMerchant() != null ? payment.getMerchant().getId() : null,
                payment.getOrderId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                attemptDtos,
                redirectUrl,
                metadata != null ? metadata : Map.of(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
