package com.payflow.payment.domain;

public enum AttemptStatus {
    CREATED,
    SUBMITTED,
    REQUIRES_ACTION,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    RECONCILIATION_REQUIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean isAmbiguous() {
        return this == UNKNOWN || this == RECONCILIATION_REQUIRED || this == PENDING;
    }
}

