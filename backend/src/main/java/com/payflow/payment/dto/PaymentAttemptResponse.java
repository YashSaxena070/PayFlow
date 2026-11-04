package com.payflow.payment.dto;

import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.PaymentAttempt;

import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID id,
        String providerCode,
        int attemptNumber,
        AttemptStatus status,
        String providerReference,
        Long amountMinor,
        String failureCode,
        String failureMessageSafe,
        Instant submittedAt,
        Instant completedAt
) {
    public static PaymentAttemptResponse from(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.getId(),
                attempt.getProvider() != null ? attempt.getProvider().getCode() : null,
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getProviderReference(),
                attempt.getAmountMinor(),
                attempt.getFailureCode(),
                attempt.getFailureMessageSafe(),
                attempt.getSubmittedAt(),
                attempt.getCompletedAt()
        );
    }
}

