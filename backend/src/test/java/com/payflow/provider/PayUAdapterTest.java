package com.payflow.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.provider.adapter.payu.PayUAdapter;
import com.payflow.provider.port.PaymentProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PayUAdapterTest {

    private PayUAdapter payUAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String key = "test_payu_merchant_key";
    private final String salt = "test_payu_merchant_salt";

    @BeforeEach
    void setUp() {
        payUAdapter = new PayUAdapter(
                key,
                salt,
                "https://test.payu.in",
                "https://test.payu.in/merchant/postservice.php?form=2",
                objectMapper
        );
    }

    @Test
    @DisplayName("Should return correct capabilities for PayU India")
    void testCapabilities() {
        assertEquals("payu", payUAdapter.getProviderCode());
        PaymentProviderPort.ProviderCapabilities caps = payUAdapter.capabilities();
        assertTrue(caps.supportedCurrencies().contains("INR"));
        assertTrue(caps.supportedMethods().contains("UPI"));
        assertTrue(caps.supportedMethods().contains("NETBANKING"));
        assertTrue(caps.supportedMethods().contains("CARD"));
    }

    @Test
    @DisplayName("Should initiate PayU payment and return transaction reference")
    void testInitiatePayment() {
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                UUID.randomUUID(),
                "ORD-PAYU-001",
                125000L,
                "INR",
                "CARD",
                "https://example.com/return",
                "SUCCESS"
        );

        PaymentProviderPort.InitiatePaymentResult result = payUAdapter.initiatePayment(command);
        assertNotNull(result.providerReference());
        assertEquals(AttemptStatus.SUCCEEDED, result.status());
    }

    @Test
    @DisplayName("Should verify PayU reverse SHA-512 response hash")
    void testVerifyWebhookHash() throws Exception {
        String status = "success";
        String txnid = "txnid_payu_12345";
        String amount = "1250.00";
        String productinfo = "Product";
        String firstname = "John";
        String email = "john@example.com";

        // Reverse hash: sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
        String reverseSequence = String.format("%s|%s|||||||||||%s|%s|%s|%s|%s|%s",
                salt, status, email, firstname, productinfo, amount, txnid, key);

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        String hash = HexFormat.of().formatHex(md.digest(reverseSequence.getBytes(StandardCharsets.UTF_8)));

        String rawBody = String.format("""
                {
                  "status": "%s",
                  "txnid": "%s",
                  "amount": "%s",
                  "productinfo": "%s",
                  "firstname": "%s",
                  "email": "%s",
                  "hash": "%s"
                }
                """, status, txnid, amount, productinfo, firstname, email, hash);

        assertTrue(payUAdapter.verifyWebhook(Map.of(), rawBody));
    }

    @Test
    @DisplayName("Should parse PayU webhook correctly")
    void testParseWebhook() {
        String rawBody = """
                {
                  "status": "success",
                  "txnid": "txn_payu_9999",
                  "mihpayid": "mih_9999_8888",
                  "amount": "150.00"
                }
                """;

        PaymentProviderPort.ParsedWebhookEvent event = payUAdapter.parseWebhook(rawBody);
        assertEquals("mih_9999_8888", event.providerEventId());
        assertEquals("txn_payu_9999", event.providerPaymentReference());
        assertEquals(AttemptStatus.SUCCEEDED, event.normalizedStatus());
        assertEquals(15000L, event.amountMinor());
        assertEquals("INR", event.currency());
    }
}

