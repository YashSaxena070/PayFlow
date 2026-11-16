package com.payflow.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.adapter.stripe.StripeAdapter;
import com.payflow.provider.port.PaymentProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StripeAdapterTest {

    private StripeAdapter stripeAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        stripeAdapter = new StripeAdapter(
                "sk_test_payflow_stripe_dummy",
                "whsec_test_stripe_secret_12345",
                objectMapper
        );
    }

    @Test
    @DisplayName("Should return correct capabilities for Stripe")
    void testCapabilities() {
        assertEquals("stripe", stripeAdapter.getProviderCode());
        PaymentProviderPort.ProviderCapabilities caps = stripeAdapter.capabilities();
        assertTrue(caps.supportedCurrencies().contains("USD"));
        assertTrue(caps.supportedCurrencies().contains("INR"));
        assertTrue(caps.supportedMethods().contains("CARD"));
        assertTrue(caps.supports3DS());
        assertTrue(caps.supportsRefunds());
        assertTrue(caps.supportsWebhooks());
    }

    @Test
    @DisplayName("Should successfully initiate payment in simulation mode")
    void testInitiatePaymentSuccess() {
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                UUID.randomUUID(),
                "ORD-STRIPE-001",
                5000L,
                "USD",
                "CARD",
                "https://example.com/return",
                "SUCCESS"
        );

        PaymentProviderPort.InitiatePaymentResult result = stripeAdapter.initiatePayment(command);
        assertNotNull(result.providerReference());
        assertTrue(result.providerReference().startsWith("ch_stripe_"));
        assertEquals(AttemptStatus.SUCCEEDED, result.status());
    }

    @Test
    @DisplayName("Should simulate ambiguous timeout correctly (AC-06)")
    void testInitiatePaymentTimeout() {
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                UUID.randomUUID(),
                "ORD-STRIPE-TIMEOUT",
                5000L,
                "USD",
                "CARD",
                "https://example.com/return",
                "TIMEOUT"
        );

        PaymentProviderPort.InitiatePaymentResult result = stripeAdapter.initiatePayment(command);
        assertEquals(AttemptStatus.UNKNOWN, result.status());
        assertEquals("GATEWAY_TIMEOUT", result.failureCode());
    }

    @Test
    @DisplayName("Should process refund successfully")
    void testRefundSuccess() {
        PaymentProviderPort.ProviderRefundCommand command = new PaymentProviderPort.ProviderRefundCommand(
                "ch_stripe_mock_ref_1234",
                UUID.randomUUID(),
                1500L,
                "USD",
                "Customer return",
                "SUCCESS"
        );

        PaymentProviderPort.ProviderRefundResult result = stripeAdapter.refund(command);
        assertTrue(result.success());
        assertNotNull(result.providerRefundReference());
        assertTrue(result.providerRefundReference().startsWith("re_stripe_"));
    }

    @Test
    @DisplayName("Should parse Stripe payment_intent.succeeded webhook payload")
    void testParseWebhook() {
        String payload = """
                {
                  "id": "evt_test_12345",
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "pi_test_98765",
                      "amount": 4999,
                      "currency": "usd"
                    }
                  }
                }
                """;

        PaymentProviderPort.ParsedWebhookEvent event = stripeAdapter.parseWebhook(payload);
        assertEquals("evt_test_12345", event.providerEventId());
        assertEquals("payment_intent.succeeded", event.eventType());
        assertEquals("pi_test_98765", event.providerPaymentReference());
        assertEquals(AttemptStatus.SUCCEEDED, event.normalizedStatus());
        assertEquals(4999L, event.amountMinor());
        assertEquals("USD", event.currency());
    }
}

