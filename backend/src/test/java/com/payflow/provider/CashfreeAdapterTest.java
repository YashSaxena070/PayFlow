package com.payflow.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.adapter.cashfree.CashfreeAdapter;
import com.payflow.provider.port.PaymentProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CashfreeAdapterTest {

    private CashfreeAdapter cashfreeAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String secretKey = "cf_secret_key_dummy";

    @BeforeEach
    void setUp() {
        cashfreeAdapter = new CashfreeAdapter(
                "TEST_CF_APP_ID_DUMMY",
                secretKey,
                "2023-08-01",
                "https://sandbox.cashfree.com/pg",
                objectMapper
        );
    }

    @Test
    @DisplayName("Should return correct capabilities for Cashfree Payments")
    void testCapabilities() {
        assertEquals("cashfree", cashfreeAdapter.getProviderCode());
        PaymentProviderPort.ProviderCapabilities caps = cashfreeAdapter.capabilities();
        assertTrue(caps.supportedCurrencies().contains("INR"));
        assertTrue(caps.supportedCurrencies().contains("USD"));
        assertTrue(caps.supportedMethods().contains("UPI"));
        assertTrue(caps.supportedMethods().contains("NETBANKING"));
        assertTrue(caps.supportedMethods().contains("CARD"));
    }

    @Test
    @DisplayName("Should initiate Cashfree payment and return order reference")
    void testInitiatePayment() {
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                UUID.randomUUID(),
                "ORD-CF-001",
                45000L,
                "INR",
                "UPI",
                "https://example.com/return",
                "SUCCESS"
        );

        PaymentProviderPort.InitiatePaymentResult result = cashfreeAdapter.initiatePayment(command);
        assertNotNull(result.providerReference());
        assertEquals(AttemptStatus.SUCCEEDED, result.status());
    }

    @Test
    @DisplayName("Should verify valid HMAC-SHA256 Cashfree webhook signature")
    void testWebhookSignatureVerification() throws Exception {
        String timestamp = "1672531199";
        String rawBody = """
                {"data":{"order":{"order_id":"cf_ord_12345","order_amount":450.00,"order_currency":"INR"},"payment":{"payment_status":"SUCCESS"}},"type":"PAYMENT_SUCCESS_WEBHOOK"}
                """;

        String payloadToSign = timestamp + rawBody;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(payloadToSign.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> headers = Map.of(
                "x-webhook-signature", signature,
                "x-webhook-timestamp", timestamp
        );

        assertTrue(cashfreeAdapter.verifyWebhook(headers, rawBody));
    }

    @Test
    @DisplayName("Should parse Cashfree webhook correctly")
    void testParseWebhook() {
        String rawBody = """
                {
                  "type": "PAYMENT_SUCCESS_WEBHOOK",
                  "event_time": "2026-11-17T00:30:00Z",
                  "data": {
                    "order": {
                      "order_id": "cf_ord_9999",
                      "order_amount": 199.00,
                      "order_currency": "INR"
                    },
                    "payment": {
                      "payment_status": "SUCCESS"
                    }
                  }
                }
                """;

        PaymentProviderPort.ParsedWebhookEvent event = cashfreeAdapter.parseWebhook(rawBody);
        assertEquals("2026-11-17T00:30:00Z", event.providerEventId());
        assertEquals("PAYMENT_SUCCESS_WEBHOOK", event.eventType());
        assertEquals("cf_ord_9999", event.providerPaymentReference());
        assertEquals(AttemptStatus.SUCCEEDED, event.normalizedStatus());
        assertEquals(19900L, event.amountMinor());
        assertEquals("INR", event.currency());
    }
}

