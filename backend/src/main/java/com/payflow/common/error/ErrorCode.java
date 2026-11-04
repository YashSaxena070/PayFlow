package com.payflow.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "The request was invalid or malformed"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication credentials are missing or invalid"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access to the requested resource is denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key was already used with a different request payload"),
    STATE_TRANSITION_CONFLICT(HttpStatus.CONFLICT, "The requested state transition is not allowed from current state"),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Payment cannot be refunded in current state"),
    REFUND_EXCEEDS_PAYMENT_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount exceeds refundable balance"),
    REFUND_EXCEEDS_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount exceeds remaining captured refundable balance"),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Webhook signature verification failed"),
    PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "The upstream payment provider returned an error"),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is currently unavailable or circuit open"),
    CIRCUIT_BREAKER_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "Circuit breaker is open for payment provider"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Request rate limit exceeded"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

