package com.payflow.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.merchant.domain.Merchant;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.reconciliation.domain.ReconciliationCase;
import com.payflow.reconciliation.service.ReconciliationService;
import com.payflow.test.fakes.FakeOutboxEventRepository;
import com.payflow.test.fakes.FakePaymentAttemptRepository;
import com.payflow.test.fakes.FakePaymentRepository;
import com.payflow.test.fakes.FakeReconciliationCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ReconciliationServiceTest {

    private FakeReconciliationCaseRepository caseRepository;
    private FakePaymentAttemptRepository attemptRepository;
    private FakePaymentRepository paymentRepository;
    private FakeOutboxEventRepository outboxEventRepository;
    private ReconTestAdapter adapter;
    private ReconciliationService reconciliationService;

    private Payment payment;
    private PaymentAttempt attempt;
    private Provider provider;

    @BeforeEach
    void setUp() throws Exception {
        caseRepository = new FakeReconciliationCaseRepository();
        attemptRepository = new FakePaymentAttemptRepository();
        paymentRepository = new FakePaymentRepository();
        outboxEventRepository = new FakeOutboxEventRepository();

        provider = new Provider(UUID.randomUUID(), "STRIPE", "Stripe Simulator", true);
        adapter = new ReconTestAdapter();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(adapter));

        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme Corp", "ACTIVE");
        payment = new Payment(UUID.randomUUID(), merchant, "order-recon-1", 8000L, "USD", "CARD", "key-rec", "hash-rec");
        payment.setStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1, 8000L);
        attempt.setStatus(AttemptStatus.UNKNOWN);
        attempt.setProviderReference("pi_recon_123");

        // Set submittedAt in past (e.g. 10 minutes ago)
        Field submittedAtField = PaymentAttempt.class.getDeclaredField("submittedAt");
        submittedAtField.setAccessible(true);
        submittedAtField.set(attempt, Instant.now().minusSeconds(600));

        attemptRepository.save(attempt);
        payment.addAttempt(attempt);

        OutboxService outboxService = new OutboxService(outboxEventRepository, new ObjectMapper());

        reconciliationService = new ReconciliationService(
                caseRepository,
                attemptRepository,
                paymentRepository,
                providerRegistry,
                outboxService
        );
    }

    @Test
    @DisplayName("AC-12: Stale attempt auto-resolved when provider reports terminal SUCCEEDED status")
    void testStaleAttemptAutoResolvedWhenProviderTerminal() {
        adapter.setProviderStatus(new PaymentProviderPort.ProviderPaymentStatus(
                "pi_recon_123", AttemptStatus.SUCCEEDED, null, null
        ));

        List<ReconciliationCase> cases = reconciliationService.scanStaleAttempts(300);

        assertEquals(1, cases.size());
        ReconciliationCase recCase = cases.get(0);
        assertEquals("AUTO_RESOLVED", recCase.getCaseStatus());
        assertEquals("AUTO_SYNC", recCase.getResolution());

        assertEquals(AttemptStatus.SUCCEEDED, attempt.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(1, outboxEventRepository.events.size());
    }

    @Test
    @DisplayName("AC-12: Stale attempt creates OPEN case when provider is unreachable or ambiguous")
    void testStaleAttemptOpensCaseWhenProviderUnreachable() {
        adapter.setProviderStatus(null); // Unreachable

        List<ReconciliationCase> cases = reconciliationService.scanStaleAttempts(300);

        assertEquals(1, cases.size());
        ReconciliationCase recCase = cases.get(0);
        assertEquals("OPEN", recCase.getCaseStatus());
        assertEquals("UNKNOWN", recCase.getExpectedStatus());
        assertEquals("UNREACHABLE", recCase.getProviderStatus());

        assertEquals(AttemptStatus.UNKNOWN, attempt.getStatus());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    @DisplayName("Manual case resolution transitions case to RESOLVED and updates payment")
    void testManualCaseResolution() {
        adapter.setProviderStatus(null);
        reconciliationService.scanStaleAttempts(300);

        ReconciliationCase openCase = caseRepository.db.values().iterator().next();
        assertEquals("OPEN", openCase.getCaseStatus());

        ReconciliationCase resolved = reconciliationService.resolveCase(
                openCase.getId(), "MARK_FAILED", "Confirmed charge was not captured at bank");

        assertEquals("RESOLVED", resolved.getCaseStatus());
        assertEquals("MARK_FAILED", resolved.getResolution());
        assertEquals(AttemptStatus.FAILED, attempt.getStatus());
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertNotNull(resolved.getResolvedAt());
    }

    static class ReconTestAdapter implements PaymentProviderPort {
        private ProviderPaymentStatus providerStatus;

        public void setProviderStatus(ProviderPaymentStatus providerStatus) {
            this.providerStatus = providerStatus;
        }

        @Override public String getProviderCode() { return "STRIPE"; }
        @Override public ProviderCapabilities capabilities() {
            return new ProviderCapabilities(List.of("USD"), List.of("CARD"), true, true, true);
        }
        @Override public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) { return null; }
        @Override public ProviderPaymentStatus getPaymentStatus(String providerReference) { return providerStatus; }
        @Override public ProviderRefundResult refund(ProviderRefundCommand command) { return null; }
        @Override public boolean verifyWebhook(Map<String, String> headers, String rawBody) { return true; }
        @Override public ParsedWebhookEvent parseWebhook(String rawBody) { return null; }
    }
}

