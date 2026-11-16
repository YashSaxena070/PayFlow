package com.payflow.provider.adapter.cashfree;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("cashfreeProvider")
public class CashfreeAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(CashfreeAdapter.class);
    private static final String PROVIDER_CODE = "cashfree";

    private final String appId;
    private final String secretKey;
    private final String apiVersion;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CashfreeAdapter(
            @Value("${payflow.providers.cashfree.app-id:TEST_CF_APP_ID_DUMMY}") String appId,
            @Value("${payflow.providers.cashfree.secret-key:cf_secret_key_dummy}") String secretKey,
            @Value("${payflow.providers.cashfree.api-version:2023-08-01}") String apiVersion,
            @Value("${payflow.providers.cashfree.base-url:https://sandbox.cashfree.com/pg}") String baseUrl,
            ObjectMapper objectMapper) {
        this.appId = appId;
        this.secretKey = secretKey;
        this.apiVersion = apiVersion;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(
                List.of("INR", "USD"),
                List.of("CARD", "UPI", "NETBANKING", "WALLET"),
                true,
                true,
                true
        );
    }

    @Override
    public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) {
        String scenario = resolveScenario(command.testScenario(), command.orderId());
        log.info("Cashfree adapter executing payment for orderId={}, scenario={}, isLiveConfigured={}",
                command.orderId(), scenario, isLiveConfigured());

        if (scenario != null || !isLiveConfigured()) {
            return simulateInitiatePayment(command, scenario != null ? scenario : "SUCCESS");
        }

        try {
            double orderAmount = command.amountMinor() / 100.0;
            String returnUrl = command.returnUrl() != null ? command.returnUrl() : "https://merchant.example.com/checkout/return";

            Map<String, Object> orderPayload = Map.of(
                    "order_id", command.orderId(),
                    "order_amount", orderAmount,
                    "order_currency", command.currency().toUpperCase(),
                    "customer_details", Map.of(
                            "customer_id", "cust_" + UUID.randomUUID().toString().substring(0, 8),
                            "customer_phone", "9999999999",
                            "customer_email", "customer@example.com"
                    ),
                    "order_meta", Map.of(
                            "return_url", returnUrl
                    )
            );

            String requestBody = objectMapper.writeValueAsString(orderPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/orders"))
                    .header("x-client-id", appId)
                    .header("x-client-secret", secretKey)
                    .header("x-api-version", apiVersion)
                    .header("x-idempotency-key", command.paymentId().toString())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("Cashfree order creation error status {}: {}", response.statusCode(), response.body());
                return new InitiatePaymentResult(
                        null,
                        AttemptStatus.FAILED,
                        "CASHFREE_HTTP_" + response.statusCode(),
                        "Cashfree order creation rejected: " + response.body(),
                        null,
                        "Cashfree Error"
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            String cfOrderId = root.path("cf_order_id").asText();
            String paymentSessionId = root.path("payment_session_id").asText();
            String paymentUrl = root.path("payments").path("url").asText(null);

            Map<String, Object> metadata = Map.of(
                    "cfOrderId", cfOrderId,
                    "paymentSessionId", paymentSessionId,
                    "orderStatus", root.path("order_status").asText("ACTIVE")
            );

            return new InitiatePaymentResult(
                    cfOrderId,
                    AttemptStatus.REQUIRES_ACTION,
                    null,
                    null,
                    paymentUrl,
                    "Cashfree Order Created",
                    metadata
            );
        } catch (Exception e) {
            log.error("Unhandled Cashfree order creation exception", e);
            return new InitiatePaymentResult(
                    null,
                    AttemptStatus.UNKNOWN,
                    "CASHFREE_COMMUNICATION_ERROR",
                    e.getMessage(),
                    null,
                    "Cashfree Error"
            );
        }
    }

    @Override
    public ProviderPaymentStatus getPaymentStatus(String providerReference) {
        if (providerReference == null) {
            return new ProviderPaymentStatus(null, AttemptStatus.UNKNOWN, "NO_REF", "Missing reference");
        }

        if (!isLiveConfigured() || providerReference.startsWith("cf_sim_")) {
            if (providerReference.contains("declined")) {
                return new ProviderPaymentStatus(providerReference, AttemptStatus.FAILED, "EXPIRED", "Payment expired or declined");
            }
            return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/orders/" + providerReference))
                    .header("x-client-id", appId)
                    .header("x-client-secret", secretKey)
                    .header("x-api-version", apiVersion)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            String orderStatus = root.path("order_status").asText("ACTIVE");
            AttemptStatus attemptStatus = switch (orderStatus.toUpperCase()) {
                case "PAID" -> AttemptStatus.SUCCEEDED;
                case "EXPIRED", "TERMINATED" -> AttemptStatus.FAILED;
                default -> AttemptStatus.PENDING;
            };

            return new ProviderPaymentStatus(providerReference, attemptStatus, null, null);
        } catch (Exception e) {
            log.warn("Failed to check Cashfree order status for {}", providerReference, e);
            return new ProviderPaymentStatus(providerReference, AttemptStatus.UNKNOWN, "STATUS_CHECK_FAILED", e.getMessage());
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        if ("FAIL".equalsIgnoreCase(command.testScenario())) {
            return new ProviderRefundResult(null, false, "REFUND_REJECTED", "Cashfree simulated refund failure");
        }

        if (!isLiveConfigured() || command.providerPaymentReference().startsWith("cf_sim_")) {
            String refundRef = "cf_rfnd_" + UUID.randomUUID().toString().substring(0, 12);
            return new ProviderRefundResult(refundRef, true, null, null);
        }

        try {
            double refundAmount = command.amountMinor() / 100.0;
            String refundId = "rfnd_" + command.refundId().toString().substring(0, 8);

            Map<String, Object> refundPayload = Map.of(
                    "refund_amount", refundAmount,
                    "refund_id", refundId,
                    "refund_note", command.reason() != null ? command.reason() : "Customer refund"
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/orders/" + command.providerPaymentReference() + "/refunds"))
                    .header("x-client-id", appId)
                    .header("x-client-secret", secretKey)
                    .header("x-api-version", apiVersion)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(refundPayload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            String cfRefundId = root.path("cf_refund_id").asText(refundId);
            String refundStatus = root.path("refund_status").asText("SUCCESS");
            boolean success = "SUCCESS".equalsIgnoreCase(refundStatus) || "PENDING".equalsIgnoreCase(refundStatus);

            return new ProviderRefundResult(cfRefundId, success, null, null);
        } catch (Exception e) {
            log.error("Cashfree refund failed", e);
            return new ProviderRefundResult(null, false, "CASHFREE_REFUND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return false;
        String signature = headers.getOrDefault("x-webhook-signature", headers.get("X-Webhook-Signature"));
        String timestamp = headers.getOrDefault("x-webhook-timestamp", headers.get("X-Webhook-Timestamp"));

        if (signature == null || timestamp == null) return false;

        try {
            String payloadToSign = timestamp + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8));
            String expectedBase64 = Base64.getEncoder().encodeToString(hash);

            return MessageDigest.isEqual(expectedBase64.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Cashfree webhook signature verification error", e);
            return false;
        }
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String type = root.path("type").asText("PAYMENT_SUCCESS_WEBHOOK");
            String eventTime = root.path("event_time").asText(UUID.randomUUID().toString());

            JsonNode data = root.path("data");
            JsonNode order = data.path("order");
            JsonNode payment = data.path("payment");

            String orderId = order.path("order_id").asText(null);
            double orderAmount = order.path("order_amount").asDouble(0.0);
            long amountMinor = Math.round(orderAmount * 100.0);
            String currency = order.path("order_currency").asText("INR").toUpperCase();

            String paymentStatus = payment.path("payment_status").asText("SUCCESS");
            AttemptStatus status = "SUCCESS".equalsIgnoreCase(paymentStatus) ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;

            return new ParsedWebhookEvent(
                    eventTime,
                    type,
                    orderId,
                    status,
                    amountMinor,
                    currency,
                    null,
                    null
            );
        } catch (Exception e) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Malformed Cashfree webhook payload");
        }
    }

    private boolean isLiveConfigured() {
        return appId != null && !appId.isBlank() && !appId.contains("DUMMY") && !appId.contains("TEST_CF_APP_ID");
    }

    private InitiatePaymentResult simulateInitiatePayment(InitiatePaymentCommand command, String scenario) {
        String ref = "cf_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        switch (scenario) {
            case "TIMEOUT" -> {
                log.warn("Cashfree adapter simulating HTTP socket timeout for orderId={}", command.orderId());
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.UNKNOWN,
                        "GATEWAY_TIMEOUT",
                        "Provider call timed out after request transmission. Status is ambiguous.",
                        null,
                        "Cashfree Gateway Timeout Simulation"
                );
            }
            case "DECLINE" -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.FAILED,
                        "PAYMENT_REJECTED",
                        "Cashfree simulated payment rejection.",
                        null,
                        "Cashfree Simulated Decline"
                );
            }
            case "REQUIRES_ACTION" -> {
                String redirectUrl = "https://sandbox.cashfree.com/pg/orders/" + ref + "/pay";
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.REQUIRES_ACTION,
                        null,
                        null,
                        redirectUrl,
                        "Cashfree Checkout Session Active",
                        Map.of("cfOrderId", ref, "paymentSessionId", "session_" + UUID.randomUUID().toString().substring(0, 16))
                );
            }
            case "5XX" -> throw new PayflowException(ErrorCode.PROVIDER_ERROR, "Cashfree API returned 502 Bad Gateway");
            default -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.SUCCEEDED,
                        null,
                        null,
                        null,
                        "Cashfree Payment Succeeded",
                        Map.of("cfOrderId", ref)
                );
            }
        }
    }

    private String resolveScenario(String scenario, String orderId) {
        if (scenario != null && !scenario.isBlank() && !scenario.equalsIgnoreCase("REAL") && !scenario.equalsIgnoreCase("LIVE") && !scenario.equalsIgnoreCase("NONE")) return scenario.toUpperCase();
        if (orderId != null) {
            if (orderId.contains("SIM_TIMEOUT")) return "TIMEOUT";
            if (orderId.contains("SIM_DECLINE")) return "DECLINE";
            if (orderId.contains("SIM_3DS") || orderId.contains("SIM_REQUIRES_ACTION")) return "REQUIRES_ACTION";
            if (orderId.contains("SIM_5XX")) return "5XX";
        }
        return null;
    }
}

