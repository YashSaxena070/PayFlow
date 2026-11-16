package com.payflow.provider.adapter.payu;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component("payuProvider")
public class PayUAdapter implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(PayUAdapter.class);
    private static final String PROVIDER_CODE = "payu";

    private final String key;
    private final String salt;
    private final String baseUrl;
    private final String serviceUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PayUAdapter(
            @Value("${payflow.providers.payu.key:test_payu_merchant_key}") String key,
            @Value("${payflow.providers.payu.salt:test_payu_merchant_salt}") String salt,
            @Value("${payflow.providers.payu.base-url:https://test.payu.in}") String baseUrl,
            @Value("${payflow.providers.payu.service-url:https://test.payu.in/merchant/postservice.php?form=2}") String serviceUrl,
            ObjectMapper objectMapper) {
        this.key = key;
        this.salt = salt;
        this.baseUrl = baseUrl;
        this.serviceUrl = serviceUrl;
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
                List.of("INR"),
                List.of("CARD", "UPI", "NETBANKING", "WALLET"),
                true,
                true,
                true
        );
    }

    @Override
    public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) {
        String scenario = resolveScenario(command.testScenario(), command.orderId());
        log.info("PayU adapter executing payment for orderId={}, scenario={}, isLiveConfigured={}",
                command.orderId(), scenario, isLiveConfigured());

        if (scenario != null || !isLiveConfigured()) {
            return simulateInitiatePayment(command, scenario != null ? scenario : "SUCCESS");
        }

        try {
            String txnid = "payu_txn_" + command.orderId().replaceAll("[^a-zA-Z0-9_]", "") + "_" + UUID.randomUUID().toString().substring(0, 8);
            double amountMajor = command.amountMinor() / 100.0;
            String amountFormatted = String.format("%.2f", amountMajor);
            String productInfo = "PayFlow-" + command.orderId();
            String firstName = "Customer";
            String email = "customer@example.com";
            String surl = command.returnUrl() != null ? command.returnUrl() : "https://merchant.example.com/payu/success";
            String furl = command.returnUrl() != null ? command.returnUrl() : "https://merchant.example.com/payu/failure";

            // PayU SHA-512 Hash sequence:
            // sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT)
            String hashSequence = String.format("%s|%s|%s|%s|%s|%s||||||||||%s",
                    key, txnid, amountFormatted, productInfo, firstName, email, salt);
            String hash = calculateSha512(hashSequence);

            Map<String, Object> formFields = Map.of(
                    "key", key,
                    "txnid", txnid,
                    "amount", amountFormatted,
                    "productinfo", productInfo,
                    "firstname", firstName,
                    "email", email,
                    "surl", surl,
                    "furl", furl,
                    "hash", hash
            );

            String checkoutUrl = baseUrl + "/_payment";

            return new InitiatePaymentResult(
                    txnid,
                    AttemptStatus.REQUIRES_ACTION,
                    null,
                    null,
                    checkoutUrl,
                    "PayU Hosted Checkout Form",
                    formFields
            );
        } catch (Exception e) {
            log.error("PayU payment initiation failed", e);
            return new InitiatePaymentResult(
                    null,
                    AttemptStatus.FAILED,
                    "PAYU_INITIATION_ERROR",
                    e.getMessage(),
                    null,
                    "PayU Error"
            );
        }
    }

    @Override
    public ProviderPaymentStatus getPaymentStatus(String providerReference) {
        if (providerReference == null) {
            return new ProviderPaymentStatus(null, AttemptStatus.UNKNOWN, "NO_REF", "Missing reference");
        }

        if (!isLiveConfigured() || providerReference.startsWith("payu_sim_")) {
            if (providerReference.contains("declined")) {
                return new ProviderPaymentStatus(providerReference, AttemptStatus.FAILED, "E1001", "Declined by bank");
            }
            return new ProviderPaymentStatus(providerReference, AttemptStatus.SUCCEEDED, null, null);
        }

        try {
            // PayU verify_payment hash: sha512(key|verify_payment|txnid|salt)
            String hashSequence = String.format("%s|verify_payment|%s|%s", key, providerReference, salt);
            String hash = calculateSha512(hashSequence);

            String formBody = String.format("key=%s&command=verify_payment&var1=%s&hash=%s", key, providerReference, hash);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            JsonNode transactionDetails = root.path("transaction_details").path(providerReference);
            String status = transactionDetails.path("status").asText("pending");

            AttemptStatus attemptStatus = switch (status.toLowerCase()) {
                case "success" -> AttemptStatus.SUCCEEDED;
                case "failure" -> AttemptStatus.FAILED;
                default -> AttemptStatus.PENDING;
            };

            return new ProviderPaymentStatus(providerReference, attemptStatus, null, null);
        } catch (Exception e) {
            log.warn("Failed to verify PayU payment status for txnid: {}", providerReference, e);
            return new ProviderPaymentStatus(providerReference, AttemptStatus.UNKNOWN, "STATUS_CHECK_FAILED", e.getMessage());
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundCommand command) {
        if ("FAIL".equalsIgnoreCase(command.testScenario())) {
            return new ProviderRefundResult(null, false, "REFUND_FAILED", "PayU simulated refund failure");
        }

        if (!isLiveConfigured() || command.providerPaymentReference().startsWith("payu_sim_")) {
            String refundRef = "payu_rfnd_" + UUID.randomUUID().toString().substring(0, 12);
            return new ProviderRefundResult(refundRef, true, null, null);
        }

        try {
            double amountMajor = command.amountMinor() / 100.0;
            String amountFormatted = String.format("%.2f", amountMajor);

            // sha512(key|cancel_refund_transaction|payuid|salt)
            String hashSequence = String.format("%s|cancel_refund_transaction|%s|%s", key, command.providerPaymentReference(), salt);
            String hash = calculateSha512(hashSequence);

            String formBody = String.format("key=%s&command=cancel_refund_transaction&var1=%s&var2=%s&var3=%s&hash=%s",
                    key, command.providerPaymentReference(), command.refundId().toString(), amountFormatted, hash);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            boolean success = root.path("status").asInt(0) == 1;
            String refundRef = root.path("request_id").asText("rfnd_" + UUID.randomUUID().toString().substring(0, 8));

            return new ProviderRefundResult(refundRef, success, null, null);
        } catch (Exception e) {
            log.error("PayU refund execution failed", e);
            return new ProviderRefundResult(null, false, "PAYU_REFUND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return false;
        try {
            // Webhook payload from PayU is either form urlencoded or JSON
            JsonNode root = objectMapper.readTree(rawBody);
            String responseHash = root.path("hash").asText(null);
            if (responseHash == null) return false;

            String status = root.path("status").asText("");
            String txnid = root.path("txnid").asText("");
            String amount = root.path("amount").asText("");
            String productinfo = root.path("productinfo").asText("");
            String firstname = root.path("firstname").asText("");
            String email = root.path("email").asText("");
            String udf1 = root.path("udf1").asText("");
            String udf2 = root.path("udf2").asText("");
            String udf3 = root.path("udf3").asText("");
            String udf4 = root.path("udf4").asText("");
            String udf5 = root.path("udf5").asText("");

            // Reverse hash:
            // sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
            String reverseSequence = String.format("%s|%s||||||%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                    salt, status, udf5, udf4, udf3, udf2, udf1, email, firstname, productinfo, amount, txnid, key);
            String expectedHash = calculateSha512(reverseSequence);

            return expectedHash.equalsIgnoreCase(responseHash);
        } catch (Exception e) {
            log.warn("PayU webhook hash verification error", e);
            return false;
        }
    }

    @Override
    public ParsedWebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String txnid = root.path("txnid").asText(UUID.randomUUID().toString());
            String mihpayid = root.path("mihpayid").asText(txnid);
            String statusStr = root.path("status").asText("pending");
            double amountMajor = root.path("amount").asDouble(0.0);
            long amountMinor = Math.round(amountMajor * 100.0);

            AttemptStatus status = "success".equalsIgnoreCase(statusStr) ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED;

            return new ParsedWebhookEvent(
                    mihpayid,
                    "payu.payment." + statusStr.toLowerCase(),
                    txnid,
                    status,
                    amountMinor,
                    "INR",
                    null,
                    null
            );
        } catch (Exception e) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Malformed PayU webhook payload");
        }
    }

    private boolean isLiveConfigured() {
        return key != null && !key.isBlank() && !key.contains("dummy");
    }

    private String calculateSha512(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 not available", e);
        }
    }

    private InitiatePaymentResult simulateInitiatePayment(InitiatePaymentCommand command, String scenario) {
        String ref = "payu_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        switch (scenario) {
            case "TIMEOUT" -> {
                log.warn("PayU adapter simulating HTTP timeout for orderId={}", command.orderId());
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.UNKNOWN,
                        "GATEWAY_TIMEOUT",
                        "Provider call timed out after request transmission. Status is ambiguous.",
                        null,
                        "PayU Gateway Timeout Simulation"
                );
            }
            case "DECLINE" -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.FAILED,
                        "E1001",
                        "PayU simulated card rejection",
                        null,
                        "PayU Simulated Decline"
                );
            }
            case "REQUIRES_ACTION" -> {
                String redirectUrl = baseUrl + "/_payment?txnid=" + ref;
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.REQUIRES_ACTION,
                        null,
                        null,
                        redirectUrl,
                        "PayU Redirection Required",
                        Map.of("txnid", ref, "key", key)
                );
            }
            case "5XX" -> throw new PayflowException(ErrorCode.PROVIDER_ERROR, "PayU API returned 502 Bad Gateway");
            default -> {
                return new InitiatePaymentResult(
                        ref,
                        AttemptStatus.SUCCEEDED,
                        null,
                        null,
                        null,
                        "PayU Payment Succeeded",
                        Map.of("txnid", ref)
                );
            }
        }
    }

    private String resolveScenario(String scenario, String orderId) {
        if (scenario != null && !scenario.isBlank()) return scenario.toUpperCase();
        if (orderId != null) {
            if (orderId.contains("SIM_TIMEOUT")) return "TIMEOUT";
            if (orderId.contains("SIM_DECLINE")) return "DECLINE";
            if (orderId.contains("SIM_3DS") || orderId.contains("SIM_REQUIRES_ACTION")) return "REQUIRES_ACTION";
            if (orderId.contains("SIM_5XX")) return "5XX";
        }
        return null;
    }
}

