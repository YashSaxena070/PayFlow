package com.payflow.identity.domain;

public enum UserRole {
    PLATFORM_ADMIN,
    MERCHANT_ADMIN,
    MERCHANT_OPERATOR,
    READ_ONLY_ANALYST;

    public boolean canManageRouting() {
        return this == PLATFORM_ADMIN || this == MERCHANT_ADMIN;
    }

    public boolean canManageApiKeys() {
        return this == PLATFORM_ADMIN || this == MERCHANT_ADMIN;
    }

    public boolean canInitiateRefund() {
        return this == PLATFORM_ADMIN || this == MERCHANT_ADMIN || this == MERCHANT_OPERATOR;
    }

    public boolean canResolveReconciliation() {
        return this == PLATFORM_ADMIN || this == MERCHANT_ADMIN || this == MERCHANT_OPERATOR;
    }

    public boolean canCreatePayment() {
        return this == PLATFORM_ADMIN || this == MERCHANT_ADMIN || this == MERCHANT_OPERATOR;
    }

    public boolean isReadOnly() {
        return this == READ_ONLY_ANALYST;
    }
}

