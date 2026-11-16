package com.payflow.provider.adapter.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.port.PaymentProviderPort;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
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

@Component("razorpayProvider")
public class RazorpayAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(RazorpayAdapter.class);
    private static final String PROVIDER_CODE = "razorpay";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public RazorpayAdapter(
            @Value("${payflow.providers.razorpay.key-id:rzp_test_payflow_dummy}") String keyId,
            @Value("${payflow.providers.razorpay.key-secret:rzp_secret_dummy}") String keySecret,
            @Value("${payflow.providers.razorpay.webhook-secret:whsec_test_razorpay_secret_12345}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.keyId = keyId;
        this.keySecret = keySecret;
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
                List.of("INR", "USD", "EUR"),
                List.of("CARD", "UPI", "NETBANKING", "WALLET"),
                true,
                true,
                true
        );
    }

    @Override
    public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) {
        String scenario = resolveScenario(command.testScenario(), command.orderId());
        log.info("Razorpay adapter executing payment for orderId={}, scenario={}, isLiveConfigured={}",
                command.orderId(), scenario, isLiveConfigured());

        if (scenario != null || !isLiveConfigured()) {
            return simulateInitiatePayment(command, scenario != null ? scenario : "SUCCESS");
        }

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            String rzpOrderId;
            String hostedUrl = null;
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("keyId", keyId);
            metadata.put("currency", command.currency().toUpperCase());
            metadata.put("amount", command.amountMinor());
            metadata.put("provider", "razorpay");

            // 1. Create standard Razorpay Order (official for checkout.js modal & server sync)
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", command.amountMinor()); // Amount in paise/cents
            orderRequest.put("currency", command.currency().toUpperCase());
            orderRequest.put("receipt", command.orderId());
            orderRequest.put("payment_capture", 1); // Auto-capture

            Order order = client.orders.create(orderRequest);
            rzpOrderId = order.get("id");
            metadata.put("orderId", rzpOrderId);

            // 2. Also attempt PaymentLink creation so user can open a live hosted Razorpay page
            try {
                JSONObject plink = new JSONObject();
                plink.put("amount", command.amountMinor());
                plink.put("currency", command.currency().toUpperCase());
                plink.put("accept_partial", false);
                plink.put("reference_id", command.orderId());
                plink.put("description", "PayFlow Order " + command.orderId());
                JSONObject cust = new JSONObject();
                cust.put("name", "Test Customer");
                cust.put("email", "customer@payflow.dev");
                cust.put("contact", "+919876543210");
                plink.put("customer", cust);
                JSONObject notify = new JSONObject();
                notify.put("sms", false);
                notify.put("email", false);
                plink.put("notify", notify);
                if (command.returnUrl() != null) {
                    plink.put("callback_url", command.returnUrl());
                    plink.put("callback_method", "get");
                }
                com.razorpay.PaymentLink link = client.paymentLink.create(plink);
                if (link.has("short_url")) {
                    hostedUrl = link.get("short_url");
                    metadata.put("shortUrl", hostedUrl);
                    metadata.put("paymentLinkId", link.get("id"));
                }
            } catch (Exception plinkEx) {
                log.info("PaymentLink creation optional step skipped: {}", plinkEx.getMessage());
            }

            return new InitiatePaymentResult(
                    rzpOrderId,
                    AttemptStatus.REQUIRES_ACTION,
                    null,
                    null,
                    hostedUrl,
                    "Razorpay Order Created: " + rzpOrderId,
                    metadata
            );
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for orderId={}", command.orderId(), e);
            return new InitiatePaymentResult(
                    null,
                    AttemptStatus.FAILED,
                    "RAZORPAY_API_ERROR",
                    e.getMessage(),
                    null,
                    "Razorpay Error"
            );
        }
    }

    @Override
    public ProviderPaymentStatus getPaymentStatus(String providerReference) {
        if (providerReference == null) {
            return new ProviderPaymentStatus(null, AttemptStatus.UNKNOWN, "NO_REF", "Missing reference");
        }

        if (!isLiveConfigured() || providerReference.startsWith("order_sim_")) {
            if (providerReference.contains("declined")) {
                return new ProviderPaymentStatus(providerReference, AttemptStatus.FAILED, "PAYMENT_FAILED", "Declined");
            }
            return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
        }

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            if (providerReference.startsWith("order_")) {
                Order order = client.orders.fetch(providerReference);
                String status = order.get("status");
                if ("paid".equalsIgnoreCase(status)) {
                    return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
                }
                // Check if any payment was captured under this order
                try {
                    List<Payment> payments = client.orders.fetchPayments(providerReference);
                    for (Payment p : payments) {
                        String pStatus = p.get("status");
                        if ("captured".equalsIgnoreCase(pStatus)) {
                            return new ProviderPaymentStatus(p.get("id"), AttemptStatus.SUCCEEDED, null, null);
                        }
                    }
                } catch (Exception ignored) {}
                return new ProviderPaymentStatus(providerReference, AttemptStatus.PENDING, null, null);
            } else if (providerReference.startsWith("plink_")) {
                com.razorpay.PaymentLink link = client.paymentLink.fetch(providerReference);
                String status = link.get("status");
                AttemptStatus attemptStatus = "paid".equalsIgnoreCase(status) ? AttemptStatus.SUCCEEDED : AttemptStatus.PENDING;
                return new ProviderPaymentStatus(providerReference, attemptStatus, null, null);
            } else {
                Payment payment = client.payments.fetch(providerReference);
                String status = payment.get("status");
                AttemptStatus attemptStatus = switch (status.toLowerCase()) {
                    case "captured" -> AttemptStatus.SUCCEEDED;
                    case "failed" -> AttemptStatus.FAILED;
                    default -> AttemptStatus.PENDING;
                };
                return new ProviderPaymentStatus(providerReference, attemptStatus, null, null);
            }
        } catch (RazorpayException e) {
            log.warn("Failed to fetch Razorpay status for {}", providerReference, e);
            return new ProviderPaymentStatus(providerReference, AttemptStatus.UNKNOWN, "FETCH_ERROR", e.getMessage());
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        if ("FAIL".equalsIgnoreCase(command.testScenario())) {
            return new ProviderRefundResult(null, false, "refund_failed", "Razorpay refund simulated failure");
        }

        if (!isLiveConfigured() || command.providerPaymentReference().startsWith("order_sim_")) {
            String refundRef = "rfnd_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            return new ProviderRefundResult(refundRef, true, null, null);
        }

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", command.amountMinor());
            if (command.reason() != null) {
                refundRequest.put("notes", new JSONObject().put("reason", command.reason()));
            }

            // In Razorpay, refund is initiated on the payment_id (pay_...)
            String paymentRef = command.providerPaymentReference();
            com.razorpay.Refund rzpRefund = client.payments.refund(paymentRef, refundRequest);
            String refundId = rzpRefund.get("id");
            return new ProviderRefundResult(refundId, true, null, null);
        } catch (RazorpayException e) {
            log.error("Razorpay refund failed for ref {}", command.providerPaymentReference(), e);
            return new ProviderRefundResult(null, false, "REFUND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return false;
        String signature = headers.getOrDefault("x-razorpay-signature", headers.get("X-Razorpay-Signature"));
        if (signature == null) return false;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Razorpay webhook signature verification error", e);
            return false;
        }
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("event").asText("order.paid");
            String eventId = root.path("id").asText(UUID.randomUUID().toString());

            JsonNode payload = root.path("payload");
            JsonNode paymentNode = payload.path("payment").path("entity");
            JsonNode orderNode = payload.path("order").path("entity");

            String providerPaymentRef = paymentNode.hasNonNull("id") ? paymentNode.path("id").asText() : orderNode.path("id").asText(null);
            Long amountMinor = paymentNode.hasNonNull("amount") ? paymentNode.path("amount").asLong() : orderNode.path("amount").asLong(0L);
            String currency = paymentNode.hasNonNull("currency") ? paymentNode.path("currency").asText("INR").toUpperCase() : "INR";

            AttemptStatus status = switch (eventType) {
                case "order.paid", "payment.captured" -> AttemptStatus.SUCCEEDED;
                case "payment.failed" -> AttemptStatus.FAILED;
                default -> AttemptStatus.PENDING;
            };

            return new ParsedWebhookEvent(
                    eventId,
                    eventType,
                    providerPaymentRef,
                    status,
                    amountMinor,
                    currency,
                    null,
                    null
            );
        } catch (Exception e) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Malformed Razorpay webhook body");
        }
    }

    private boolean isLiveConfigured() {
        return keyId != null && !keyId.isBlank() && !keyId.contains("dummy") && keyId.startsWith("rzp_");
    }

    private InitiatePaymentResult simulateInitiatePayment(InitiatePaymentCommand command, String scenario) {
        String ref = "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        switch (scenario) {
            case "TIMEOUT" -> {
                log.warn("Razorpay adapter simulating HTTP socket timeout for orderId={}", command.orderId());
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.UNKNOWN,
                        "GATEWAY_TIMEOUT",
                        "Provider call timed out after request transmission. Status is ambiguous.",
                        null,
                        "Razorpay Gateway Timeout Simulation"
                );
            }
            case "DECLINE" -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.FAILED,
                        "payment_declined",
                        "Razorpay simulated payment rejection.",
                        null,
                        "Razorpay Simulated Decline"
                );
            }
            case "REQUIRES_ACTION" -> {
                String redirectUrl = "https://api.razorpay.com/v1/checkout/public?order_id=" + ref;
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.REQUIRES_ACTION,
                        null,
                        null,
                        redirectUrl,
                        "Razorpay Checkout Required",
                        Map.of("orderId", ref, "keyId", keyId)
                );
            }
            case "5XX" -> throw new PayflowException(ErrorCode.PROVIDER_ERROR, "Razorpay API returned 502 Bad Gateway");
            default -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.SUCCEEDED,
                        null,
                        null,
                        null,
                        "Razorpay Payment Succeeded",
                        Map.of("orderId", ref)
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
            if (orderId.contains("SIM_3DS") || orderId.contains("SIM_REQUIRES_ACTION")) return "REQUIRES_ACTION";
            if (orderId.contains("SIM_5XX")) return "5XX";
        }
        return null;
    }
}

