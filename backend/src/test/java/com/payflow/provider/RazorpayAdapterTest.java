package com.payflow.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.adapter.razorpay.RazorpayAdapter;
import com.payflow.provider.port.PaymentProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayAdapterTest {

    private RazorpayAdapter razorpayAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String webhookSecret = "whsec_test_razorpay_secret_12345";

    @BeforeEach
    void setUp() {
        razorpayAdapter = new RazorpayAdapter(
                "rzp_test_payflow_dummy",
                "rzp_secret_dummy",
                webhookSecret,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should return correct capabilities for Razorpay")
    void testCapabilities() {
        assertEquals("razorpay", razorpayAdapter.getProviderCode());
        PaymentProviderPort.ProviderCapabilities caps = razorpayAdapter.capabilities();
        assertTrue(caps.supportedCurrencies().contains("INR"));
        assertTrue(caps.supportedCurrencies().contains("USD"));
        assertTrue(caps.supportedMethods().contains("UPI"));
        assertTrue(caps.supportedMethods().contains("CARD"));
        assertTrue(caps.supportedMethods().contains("NETBANKING"));
    }

    @Test
    @DisplayName("Should initiate payment and return order reference")
    void testInitiatePayment() {
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                UUID.randomUUID(),
                "ORD-RZP-100",
                75000L,
                "INR",
                "UPI",
                "https://example.com/return",
                "SUCCESS"
        );

        PaymentProviderPort.InitiatePaymentResult result = razorpayAdapter.initiatePayment(command);
        assertNotNull(result.providerReference());
        assertEquals(AttemptStatus.SUCCEEDED, result.status());
    }

    @Test
    @DisplayName("Should verify valid HMAC-SHA256 webhook signature")
    void testWebhookSignatureVerification() throws Exception {
        String rawBody = """
                {"event":"order.paid","id":"evt_rzp_123","payload":{"order":{"entity":{"id":"order_test_456","amount":50000,"currency":"INR"}}}}
                """;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> headers = Map.of("x-razorpay-signature", signature);
        assertTrue(razorpayAdapter.verifyWebhook(headers, rawBody));

        // Invalid signature check
        Map<String, String> invalidHeaders = Map.of("x-razorpay-signature", "invalid_sig_abc");
        assertFalse(razorpayAdapter.verifyWebhook(invalidHeaders, rawBody));
    }

    @Test
    @DisplayName("Should parse order.paid webhook correctly")
    void testParseWebhook() {
        String rawBody = """
                {
                  "entity": "event",
                  "event": "order.paid",
                  "id": "evt_rzp_9999",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_8888",
                        "amount": 25000,
                        "currency": "INR",
                        "status": "captured"
                      }
                    },
                    "order": {
                      "entity": {
                        "id": "order_test_7777"
                      }
                    }
                  }
                }
                """;

        PaymentProviderPort.ParsedWebhookEvent event = razorpayAdapter.parseWebhook(rawBody);
        assertEquals("evt_rzp_9999", event.providerEventId());
        assertEquals("order.paid", event.eventType());
        assertEquals("pay_test_8888", event.providerPaymentReference());
        assertEquals(AttemptStatus.SUCCEEDED, event.normalizedStatus());
        assertEquals(25000L, event.amountMinor());
        assertEquals("INR", event.currency());
    }
}

