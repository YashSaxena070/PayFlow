package com.payflow.payment.domain;

public enum PaymentStatus {
    CREATED,
    PROCESSING,
    REQUIRES_ACTION,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == REFUNDED;
    }

    public boolean canRefund() {
        return this == SUCCEEDED || this == PARTIALLY_REFUNDED;
    }
}

