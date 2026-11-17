package com.payflow.provider.adapter.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.port.PaymentProviderPort;
import com.stripe.exception.CardException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("stripeProvider")
public class StripeAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(StripeAdapter.class);
    private static final String PROVIDER_CODE = "stripe";

    private final String apiKey;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public StripeAdapter(
            @Value("${payflow.providers.stripe.api-key:sk_test_payflow_stripe_dummy}") String apiKey,
            @Value("${payflow.providers.stripe.webhook-secret:whsec_test_stripe_secret_12345}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(
                List.of("USD", "EUR", "GBP", "INR", "SGD"),
                List.of("CARD"),
                true,
                true,
                true
        );
    }

    @Override
    public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) {
        String scenario = resolveScenario(command.testScenario(), command.orderId());
        log.info("Stripe adapter executing payment for orderId={}, scenario={}, isLiveApiKey={}",
                command.orderId(), scenario, isLiveConfigured());

        // 1. If explicit simulation scenario requested or using dummy key, run deterministic simulator
        if (scenario != null || !isLiveConfigured()) {
            return simulateInitiatePayment(command, scenario != null ? scenario : "SUCCESS");
        }

        // 2. Real Stripe PaymentIntent integration
        try {
            RequestOptions options = RequestOptions.builder()
                    .setApiKey(apiKey)
                    .setIdempotencyKey(command.paymentId().toString())
                    .build();

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(command.amountMinor())
                    .setCurrency(command.currency().toLowerCase())
                    .setDescription("PayFlow Order " + command.orderId())
                    .putMetadata("orderId", command.orderId())
                    .putMetadata("paymentId", command.paymentId().toString());

            if ("CARD".equalsIgnoreCase(command.paymentMethod())) {
                paramsBuilder.setPaymentMethod("pm_card_visa")
                             .setConfirm(true)
                             .setReturnUrl(command.returnUrl() != null ? command.returnUrl() : "https://merchant.example.com/checkout/return");
            } else {
                paramsBuilder.setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                );
            }

            PaymentIntent intent = PaymentIntent.create(paramsBuilder.build(), options);

            AttemptStatus status = mapStripeStatus(intent.getStatus());
            String redirectUrl = null;
            if (intent.getNextAction() != null && intent.getNextAction().getRedirectToUrl() != null) {
                redirectUrl = intent.getNextAction().getRedirectToUrl().getUrl();
            }

            Map<String, Object> metadata = Map.of(
                    "clientSecret", intent.getClientSecret() != null ? intent.getClientSecret() : "",
                    "stripeStatus", intent.getStatus()
            );

            return new InitiatePaymentResult(
                    intent.getId(),
                    status,
                    null,
                    null,
                    redirectUrl,
                    "Stripe PaymentIntent " + intent.getStatus(),
                    metadata
            );
        } catch (CardException ce) {
            log.warn("Stripe CardException for orderId={}: {}", command.orderId(), ce.getMessage());
            return new InitiatePaymentResult(
                    ce.getStripeError() != null ? ce.getStripeError().getPaymentIntent() != null ? ce.getStripeError().getPaymentIntent().getId() : null : null,
                    AttemptStatus.FAILED,
                    ce.getCode(),
                    ce.getMessage(),
                    null,
                    "Stripe Card Decline"
            );
        } catch (StripeException se) {
            log.error("StripeException initiating payment for orderId={}", command.orderId(), se);
            if (se.getStatusCode() != null && se.getStatusCode() >= 500) {
                throw new PayflowException(ErrorCode.PROVIDER_ERROR, "Stripe returned server error: " + se.getMessage());
            }
            return new InitiatePaymentResult(
                    null,
                    AttemptStatus.UNKNOWN,
                    "STRIPE_API_ERROR",
                    se.getMessage(),
                    null,
                    "Stripe API Error"
            );
        }
    }

    @Override
    public ProviderPaymentStatus getPaymentStatus(String providerReference) {
        if (providerReference == null) {
            return new ProviderPaymentStatus(null, AttemptStatus.UNKNOWN, "NO_REF", "Missing reference");
        }

        if (!isLiveConfigured() || providerReference.startsWith("ch_stripe_")) {
            if (providerReference.contains("declined")) {
                return new ProviderPaymentStatus(providerReference, AttemptStatus.FAILED, "card_declined", "Declined");
            }
            return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
        }

        try {
            RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();
            PaymentIntent intent = PaymentIntent.retrieve(providerReference, options);
            AttemptStatus status = mapStripeStatus(intent.getStatus());
            return new ProviderPaymentStatus(intent.getId(), status, null, null);
        } catch (StripeException e) {
            log.warn("Failed to retrieve Stripe PaymentIntent {}", providerReference, e);
            return new ProviderPaymentStatus(providerReference, AttemptStatus.UNKNOWN, "RETRIEVAL_FAILED", e.getMessage());
        }
    }

    @Override
    public ProviderCancelResult cancelPayment(String providerReference) {
        if (providerReference == null) {
            return new ProviderCancelResult(null, false, "INVALID_REF", "Reference is null");
        }

        if (!isLiveConfigured() || providerReference.startsWith("ch_stripe_")) {
            return new ProviderCancelResult(providerReference, true, null, null);
        }

        try {
            RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();
            PaymentIntent intent = PaymentIntent.retrieve(providerReference, options);
            intent.cancel(PaymentIntentCancelParams.builder().build(), options);
            return new ProviderCancelResult(providerReference, true, null, null);
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe PaymentIntent {}", providerReference, e);
            return new ProviderCancelResult(providerReference, false, e.getCode(), e.getMessage());
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        if ("FAIL".equalsIgnoreCase(command.testScenario())) {
            return new ProviderRefundResult(null, false, "charge_already_refunded", "The charge has already been fully refunded.");
        }

        if (!isLiveConfigured() || command.providerPaymentReference().startsWith("ch_stripe_")) {
            String refundRef = "re_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            return new ProviderRefundResult(refundRef, true, null, null);
        }

        try {
            RequestOptions options = RequestOptions.builder()
                    .setApiKey(apiKey)
                    .setIdempotencyKey(command.refundId().toString())
                    .build();

            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(command.providerPaymentReference())
                    .setAmount(command.amountMinor());

            if (command.reason() != null) {
                if (command.reason().toLowerCase().contains("duplicate")) {
                    paramsBuilder.setReason(RefundCreateParams.Reason.DUPLICATE);
                } else if (command.reason().toLowerCase().contains("fraud")) {
                    paramsBuilder.setReason(RefundCreateParams.Reason.FRAUDULENT);
                } else {
                    paramsBuilder.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
                }
            }

            Refund refund = Refund.create(paramsBuilder.build(), options);
            boolean success = "succeeded".equalsIgnoreCase(refund.getStatus()) || "pending".equalsIgnoreCase(refund.getStatus());
            return new ProviderRefundResult(refund.getId(), success, refund.getFailureReason(), null);
        } catch (StripeException e) {
            log.error("Stripe refund failed for payment reference {}", command.providerPaymentReference(), e);
            return new ProviderRefundResult(null, false, e.getCode(), e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return false;
        String sigHeader = headers.getOrDefault("stripe-signature", headers.get("Stripe-Signature"));
        if (sigHeader == null) return false;

        try {
            // Use official Stripe Webhook signature verification
            Event event = Webhook.constructEvent(rawBody, sigHeader, webhookSecret);
            return event != null;
        } catch (SignatureVerificationException sve) {
            log.warn("Stripe webhook signature verification failed: {}", sve.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error during Stripe webhook verification", e);
            return false;
        }
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventId = root.path("id").asText(UUID.randomUUID().toString());
            String type = root.path("type").asText("payment_intent.succeeded");

            JsonNode obj = root.path("data").path("object");
            String pspRef = obj.path("id").asText(null);
            Long amount = obj.path("amount").isNumber() ? obj.path("amount").asLong() : null;
            String currency = obj.path("currency").asText("USD").toUpperCase();

            AttemptStatus status = switch (type) {
                case "payment_intent.succeeded", "charge.succeeded" -> AttemptStatus.SUCCEEDED;
                case "payment_intent.payment_failed", "charge.failed" -> AttemptStatus.FAILED;
                case "payment_intent.requires_action" -> AttemptStatus.REQUIRES_ACTION;
                default -> AttemptStatus.PENDING;
            };

            return new ParsedWebhookEvent(eventId, type, pspRef, status, amount, currency, null, null);
        } catch (Exception e) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Malformed Stripe webhook body");
        }
    }

    private boolean isLiveConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("dummy") && apiKey.startsWith("sk_");
    }

    private AttemptStatus mapStripeStatus(String status) {
        if (status == null) return AttemptStatus.UNKNOWN;
        return switch (status.toLowerCase()) {
            case "succeeded" -> AttemptStatus.SUCCEEDED;
            case "requires_action", "requires_confirmation" -> AttemptStatus.REQUIRES_ACTION;
            case "processing" -> AttemptStatus.PENDING;
            case "canceled", "requires_payment_method" -> AttemptStatus.FAILED;
            default -> AttemptStatus.PENDING;
        };
    }

    private InitiatePaymentResult simulateInitiatePayment(InitiatePaymentCommand command, String scenario) {
        String ref = "ch_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        switch (scenario) {
            case "TIMEOUT" -> {
                log.warn("Stripe adapter simulating HTTP socket timeout for orderId={}", command.orderId());
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.UNKNOWN,
                        "GATEWAY_TIMEOUT",
                        "Provider call timed out after request transmission. Status is ambiguous.",
                        null,
                        "Stripe Gateway Timeout Simulation"
                );
            }
            case "DECLINE" -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.FAILED,
                        "card_declined",
                        "The card was declined by the issuing bank.",
                        null,
                        "Stripe Simulated Card Decline"
                );
            }
            case "REQUIRES_ACTION" -> {
                String redirectUrl = "https://hooks.stripe.com/redirect/authenticate/" + ref;
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.REQUIRES_ACTION,
                        null,
                        null,
                        redirectUrl,
                        "Stripe 3DS Verification Required"
                );
            }
            case "5XX" -> throw new PayflowException(ErrorCode.PROVIDER_ERROR, "Stripe API returned 502 Bad Gateway");
            default -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.SUCCEEDED,
                        null,
                        null,
                        null,
                        "Stripe Charge Succeeded"
                );
            }
        }
    }

    private String resolveScenario(String scenario, String orderId) {
        if (scenario != null && !scenario.isBlank() && !scenario.equalsIgnoreCase("REAL") && !scenario.equalsIgnoreCase("LIVE") && !scenario.equalsIgnoreCase("NONE")) {
            return scenario.toUpperCase();
        }
        if (orderId != null) {
            if (orderId.contains("SIM_TIMEOUT")) return "TIMEOUT";
            if (orderId.contains("SIM_DECLINE")) return "DECLINE";
            if (orderId.contains("SIM_3DS")) return "REQUIRES_ACTION";
            if (orderId.contains("SIM_5XX")) return "5XX";
        }
        return null;
    }
}

