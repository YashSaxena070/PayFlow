package com.payflow.provider.adapter.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.port.PaymentProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("stripeProvider")
public class StripeSimulatorAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(StripeSimulatorAdapter.class);
    private static final String PROVIDER_CODE = "stripe";

    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public StripeSimulatorAdapter(
            @Value("${payflow.providers.stripe.webhook-secret:whsec_test_stripe_secret_12345}") String webhookSecret,
            ObjectMapper objectMapper) {
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
        log.info("Stripe adapter initiating payment for orderId={}, scenario={}", command.orderId(), scenario);

        String ref = "ch_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        switch (scenario) {
            case "TIMEOUT" -> {
                log.warn("Stripe adapter simulating HTTP socket timeout after transmission for orderId={}", command.orderId());
                // Returns UNKNOWN status representing ambiguous network timeout
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
            case "5XX" -> {
                throw new PayflowException(ErrorCode.PROVIDER_ERROR, "Stripe API returned 502 Bad Gateway");
            }
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

    @Override
    public ProviderPaymentStatus getPaymentStatus(String providerReference) {
        if (providerReference == null) {
            return new ProviderPaymentStatus(null, AttemptStatus.UNKNOWN, "NO_REF", "Missing reference");
        }
        if (providerReference.contains("declined")) {
            return new ProviderPaymentStatus(providerReference, AttemptStatus.FAILED, "card_declined", "Declined");
        }
        return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        if ("FAIL".equalsIgnoreCase(command.testScenario())) {
            return new ProviderRefundResult(null, false, "charge_already_refunded", "The charge has already been fully refunded.");
        }
        String refundRef = "re_stripe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new ProviderRefundResult(refundRef, true, null, null);
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return false;
        String sigHeader = headers.getOrDefault("stripe-signature", headers.get("Stripe-Signature"));
        if (sigHeader == null) return false;

        String timestamp = null;
        String signature = null;

        for (String part : sigHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("t".equals(kv[0])) timestamp = kv[1];
                if ("v1".equals(kv[0])) signature = kv[1];
            }
        }

        if (timestamp == null || signature == null) return false;

        try {
            String payloadToSign = timestamp + "." + rawBody;
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(hmac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8)));

            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Stripe webhook verification error", e);
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
                default -> AttemptStatus.PENDING;
            };

            return new ParsedWebhookEvent(eventId, type, pspRef, status, amount, currency, null, null);
        } catch (Exception e) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Malformed Stripe webhook body");
        }
    }

    private String resolveScenario(String scenario, String orderId) {
        if (scenario != null && !scenario.isBlank()) {
            return scenario.toUpperCase();
        }
        if (orderId != null) {
            if (orderId.contains("SIM_TIMEOUT")) return "TIMEOUT";
            if (orderId.contains("SIM_DECLINE")) return "DECLINE";
            if (orderId.contains("SIM_3DS")) return "REQUIRES_ACTION";
            if (orderId.contains("SIM_5XX")) return "5XX";
        }
        return "SUCCESS";
    }
}

