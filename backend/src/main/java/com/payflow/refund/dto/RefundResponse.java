package com.payflow.refund.dto;

import com.payflow.refund.domain.Refund;
import com.payflow.refund.domain.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID merchantId,
        Long amountMinor,
        String currency,
        RefundStatus status,
        String idempotencyKey,
        String providerRefundReference,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPayment() != null ? refund.getPayment().getId() : null,
                refund.getMerchant() != null ? refund.getMerchant().getId() : null,
                refund.getAmountMinor(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getIdempotencyKey(),
                refund.getProviderRefundReference(),
                refund.getReason(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}

