package com.payflow.provider.port;

import com.payflow.payment.domain.AttemptStatus;

import java.util.List;
import java.util.Map;

public interface PaymentProviderPort {

    String getProviderCode();

    ProviderCapabilities capabilities();

    InitiatePaymentResult initiatePayment(InitiatePaymentCommand command);

    ProviderPaymentStatus getPaymentStatus(String providerReference);

    default ProviderCancelResult cancelPayment(String providerReference) {
        return new ProviderCancelResult(providerReference, false, "NOT_SUPPORTED", "Provider does not support cancellation");
    }

    ProviderRefundResult refund(ProviderRefundCommand command);

    boolean verifyWebhook(Map<String, String> headers, String rawBody);

    ParsedWebhookEvent parseWebhook(String rawBody);

    record ProviderCapabilities(
            List<String> supportedCurrencies,
            List<String> supportedMethods,
            boolean supports3DS,
            boolean supportsRefunds,
            boolean supportsWebhooks
    ) {}

    record InitiatePaymentCommand(
            java.util.UUID paymentId,
            String orderId,
            Long amountMinor,
            String currency,
            String paymentMethod,
            String returnUrl,
            String testScenario // E.g. SUCCESS, DECLINE, TIMEOUT, REQUIRES_ACTION, 5XX
    ) {}

    record InitiatePaymentResult(
            String providerReference,
            AttemptStatus status,
            String failureCode,
            String failureMessageSafe,
            String redirectUrl,
            String rawSummary,
            Map<String, Object> metadata
    ) {
        public InitiatePaymentResult(
                String providerReference,
                AttemptStatus status,
                String failureCode,
                String failureMessageSafe,
                String redirectUrl,
                String rawSummary) {
            this(providerReference, status, failureCode, failureMessageSafe, redirectUrl, rawSummary, Map.of());
        }
    }

    record ProviderCancelResult(
            String providerReference,
            boolean success,
            String failureCode,
            String failureMessage
    ) {}

    record ProviderPaymentStatus(
            String providerReference,
            AttemptStatus status,
            String failureCode,
            String failureMessageSafe
    ) {}

    record ProviderRefundCommand(
            String providerPaymentReference,
            java.util.UUID refundId,
            Long amountMinor,
            String currency,
            String reason,
            String testScenario
    ) {}

    record ProviderRefundResult(
            String providerRefundReference,
            boolean success,
            String failureCode,
            String failureMessage
    ) {}

    record ParsedWebhookEvent(
            String providerEventId,
            String eventType,
            String providerPaymentReference,
            AttemptStatus normalizedStatus,
            Long amountMinor,
            String currency,
            String failureCode,
            String failureMessageSafe
    ) {}
}

