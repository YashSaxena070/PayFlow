package com.payflow.provider.adapter.adyen;

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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("adyenProvider")
public class AdyenSimulatorAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(AdyenSimulatorAdapter.class);
    private static final String PROVIDER_CODE = "adyen";

    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public AdyenSimulatorAdapter(
            @Value("${payflow.providers.adyen.webhook-secret:44782ECE0522E02C8D683BD34296A4E6FE5DF46E}") String webhookSecret,
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
                List.of("CARD", "UPI", "NETBANKING"),
                true,
                true,
                true
        );
    }

    @Override
    public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) {
        String scenario = resolveScenario(command.testScenario(), command.orderId());
        log.info("Adyen adapter initiating payment for orderId={}, scenario={}", command.orderId(), scenario);

        String ref = "adyen_psp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        switch (scenario) {
            case "TIMEOUT" -> {
                log.warn("Adyen adapter simulating HTTP socket timeout after transmission for orderId={}", command.orderId());
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.UNKNOWN,
                        "GATEWAY_TIMEOUT",
                        "Provider call timed out after request transmission. Status is ambiguous.",
                        null,
                        "Adyen Gateway Timeout Simulation"
                );
            }
            case "DECLINE" -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.FAILED,
                        "Refused",
                        "Transaction refused by payment processor.",
                        null,
                        "Adyen Simulated Refusal"
                );
            }
            case "PENDING" -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.PENDING,
                        null,
                        null,
                        null,
                        "Adyen Payment Pending (UPI / Asynchronous confirmation)"
                );
            }
            case "5XX" -> {
                throw new PayflowException(ErrorCode.PROVIDER_ERROR, "Adyen checkout service returned 503 Service Unavailable");
            }
            default -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.SUCCEEDED,
                        null,
                        null,
                        null,
                        "Adyen Payment Authorised"
                );
            }
        }
    }

    @Override
    public ProviderPaymentStatus getPaymentStatus(String providerReference) {
        if (providerReference == null) {
            return new ProviderPaymentStatus(null, AttemptStatus.UNKNOWN, "NO_REF", "Missing reference");
        }
        if (providerReference.contains("declined") || providerReference.contains("refused")) {
            return new ProviderPaymentStatus(providerReference, AttemptStatus.FAILED, "Refused", "Transaction Refused");
        }
        return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        if ("FAIL".equalsIgnoreCase(command.testScenario())) {
            return new ProviderRefundResult(null, false, "InsufficientBalance", "Insufficient funds to execute refund.");
        }
        String refundRef = "re_adyen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new ProviderRefundResult(refundRef, true, null, null);
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return false;
        String signature = headers.getOrDefault("x-adyen-hmacsignature",
                headers.getOrDefault("X-Adyen-HmacSignature", headers.get("adyen-signature")));

        if (signature == null) return false;

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedBytes = hmac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedBase64 = Base64.getEncoder().encodeToString(expectedBytes);

            return MessageDigest.isEqual(expectedBase64.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Adyen webhook verification error", e);
            return false;
        }
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode item = root.path("notificationItems").get(0).path("NotificationRequestItem");
            if (item.isMissingNode()) {
                item = root;
            }

            String eventId = item.path("eventCode").asText("AUTHORISATION") + "_" + UUID.randomUUID();
            String pspRef = item.path("pspReference").asText(null);
            boolean success = "true".equalsIgnoreCase(item.path("success").asText("true"));
            String eventCode = item.path("eventCode").asText("AUTHORISATION");

            Long amount = item.path("amount").path("value").asLong(0);
            String currency = item.path("amount").path("currency").asText("USD").toUpperCase();

            AttemptStatus status;
            if ("AUTHORISATION".equalsIgnoreCase(eventCode)) {
                status = success ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            } else if ("CANCEL_OR_REFUND".equalsIgnoreCase(eventCode) || "REFUND".equalsIgnoreCase(eventCode)) {
                status = success ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;
            } else {
                status = AttemptStatus.PENDING;
            }

            return new ParsedWebhookEvent(eventId, eventCode, pspRef, status, amount, currency, null, null);
        } catch (Exception e) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Malformed Adyen webhook body");
        }
    }

    private String resolveScenario(String scenario, String orderId) {
        if (scenario != null && !scenario.isBlank()) {
            return scenario.toUpperCase();
        }
        if (orderId != null) {
            if (orderId.contains("SIM_TIMEOUT")) return "TIMEOUT";
            if (orderId.contains("SIM_DECLINE")) return "DECLINE";
            if (orderId.contains("SIM_PENDING")) return "PENDING";
            if (orderId.contains("SIM_5XX")) return "5XX";
        }
        return "SUCCESS";
    }
}

