package com.payflow.webhook;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.merchant.domain.Merchant;
import com.payflow.outbox.domain.OutboxEvent;
import com.payflow.outbox.repository.OutboxEventRepository;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.repository.ProviderRepository;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.test.fakes.FakeOutboxEventRepository;
import com.payflow.test.fakes.FakePaymentAttemptRepository;
import com.payflow.test.fakes.FakePaymentRepository;
import com.payflow.webhook.domain.WebhookEvent;
import com.payflow.webhook.repository.WebhookEventRepository;
import com.payflow.webhook.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class WebhookServiceTest {

    private FakeWebhookEventRepository webhookEventRepository;
    private FakeProviderRepository providerRepository;
    private FakePaymentAttemptRepository attemptRepository;
    private FakePaymentRepository paymentRepository;
    private FakeOutboxEventRepository outboxEventRepository;
    private WebhookTestAdapter adapter;
    private WebhookService webhookService;

    private Provider provider;
    private Payment payment;
    private PaymentAttempt attempt;

    @BeforeEach
    void setUp() {
        webhookEventRepository = new FakeWebhookEventRepository();
        providerRepository = new FakeProviderRepository();
        attemptRepository = new FakePaymentAttemptRepository();
        paymentRepository = new FakePaymentRepository();
        outboxEventRepository = new FakeOutboxEventRepository();

        provider = new Provider(UUID.randomUUID(), "STRIPE", "Stripe Simulator", true);
        providerRepository.save(provider);

        adapter = new WebhookTestAdapter();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(adapter));

        Merchant merchant = new Merchant(UUID.randomUUID(), "Acme Corp", "ACTIVE");
        payment = new Payment(UUID.randomUUID(), merchant, "order-wh-1", 5000L, "USD", "CARD", "key-wh", "hash");
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1, 5000L);
        attempt.setProviderReference("pi_stripe_webhook_123");
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attemptRepository.save(attempt);
        payment.addAttempt(attempt);

        OutboxService outboxService = new OutboxService(outboxEventRepository, new com.fasterxml.jackson.databind.ObjectMapper());

        webhookService = new WebhookService(
                webhookEventRepository,
                providerRepository,
                providerRegistry,
                attemptRepository,
                paymentRepository,
                outboxService
        );
    }

    @Test
    @DisplayName("Invalid signature throws 401 WEBHOOK_SIGNATURE_INVALID")
    void testInvalidSignatureThrows401() {
        adapter.setSignatureValid(false);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                webhookService.processWebhook("STRIPE", Map.of("Stripe-Signature", "invalid"), "{\"id\":\"evt_1\"}"));

        assertEquals(ErrorCode.WEBHOOK_SIGNATURE_INVALID, ex.getErrorCode());
        assertEquals(1, webhookEventRepository.db.size());
        assertFalse(webhookEventRepository.db.values().iterator().next().isSignatureValid());
    }

    @Test
    @DisplayName("Valid webhook transitions attempt and payment to SUCCEEDED and creates outbox event")
    void testValidWebhookTransitionsToSucceeded() {
        adapter.setSignatureValid(true);
        adapter.setParsedEvent(new PaymentProviderPort.ParsedWebhookEvent(
                "evt_stripe_success_1",
                "payment_intent.succeeded",
                "pi_stripe_webhook_123",
                AttemptStatus.SUCCEEDED,
                5000L,
                "USD",
                null,
                null
        ));

        WebhookEvent event = webhookService.processWebhook(
                "STRIPE", Map.of("Stripe-Signature", "valid"), "{\"id\":\"evt_stripe_success_1\"}");

        assertNotNull(event);
        assertEquals("PROCESSED", event.getProcessingStatus());
        assertTrue(event.isSignatureValid());

        assertEquals(AttemptStatus.SUCCEEDED, attempt.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(1, outboxEventRepository.events.size());
    }

    @Test
    @DisplayName("AC-09: Duplicate webhook with same (provider_id, provider_event_id) returns without re-executing")
    void testDuplicateWebhookDeduplicated() {
        adapter.setSignatureValid(true);
        adapter.setParsedEvent(new PaymentProviderPort.ParsedWebhookEvent(
                "evt_stripe_dup_1",
                "payment_intent.succeeded",
                "pi_stripe_webhook_123",
                AttemptStatus.SUCCEEDED,
                5000L,
                "USD",
                null,
                null
        ));

        // First delivery
        WebhookEvent first = webhookService.processWebhook(
                "STRIPE", Map.of("Stripe-Signature", "valid"), "{\"id\":\"evt_stripe_dup_1\"}");
        assertNotNull(first);
        assertEquals(1, webhookEventRepository.db.size());
        assertEquals(1, outboxEventRepository.events.size());

        // Duplicate delivery
        WebhookEvent second = webhookService.processWebhook(
                "STRIPE", Map.of("Stripe-Signature", "valid"), "{\"id\":\"evt_stripe_dup_1\"}");
        assertNotNull(second);
        assertEquals(first.getId(), second.getId());
        assertEquals(1, webhookEventRepository.db.size(), "Should not create duplicate event record");
        assertEquals(1, outboxEventRepository.events.size(), "Should not re-execute side effects or duplicate outbox events");
    }

    static class WebhookTestAdapter implements PaymentProviderPort {
        private boolean signatureValid = true;
        private ParsedWebhookEvent parsedEvent;

        public void setSignatureValid(boolean signatureValid) {
            this.signatureValid = signatureValid;
        }

        public void setParsedEvent(ParsedWebhookEvent parsedEvent) {
            this.parsedEvent = parsedEvent;
        }

        @Override public String getProviderCode() { return "STRIPE"; }
        @Override public ProviderCapabilities capabilities() {
            return new ProviderCapabilities(List.of("USD"), List.of("CARD"), true, true, true);
        }
        @Override public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) { return null; }
        @Override public ProviderPaymentStatus getPaymentStatus(String providerReference) { return null; }
        @Override public ProviderRefundResult refund(ProviderRefundCommand command) { return null; }
        @Override public boolean verifyWebhook(Map<String, String> headers, String rawBody) { return signatureValid; }
        @Override public ParsedWebhookEvent parseWebhook(String rawBody) { return parsedEvent; }
    }

    static class FakeWebhookEventRepository implements WebhookEventRepository {
        final Map<UUID, WebhookEvent> db = new HashMap<>();

        @Override public Optional<WebhookEvent> findByProviderIdAndProviderEventId(UUID providerId, String providerEventId) {
            return db.values().stream()
                    .filter(e -> e.getProvider().getId().equals(providerId) && e.getProviderEventId().equals(providerEventId))
                    .findFirst();
        }

        @Override public Page<WebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable) {
            List<WebhookEvent> list = new ArrayList<>(db.values());
            return new PageImpl<>(list, pageable, list.size());
        }

        @Override public <S extends WebhookEvent> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends WebhookEvent> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends WebhookEvent> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<WebhookEvent> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<WebhookEvent> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<WebhookEvent> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(WebhookEvent entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends WebhookEvent> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends WebhookEvent> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<WebhookEvent> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public WebhookEvent getOne(UUID uuid) { return db.get(uuid); }
        @Override public WebhookEvent getById(UUID uuid) { return db.get(uuid); }
        @Override public WebhookEvent getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends WebhookEvent> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends WebhookEvent> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends WebhookEvent> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends WebhookEvent> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends WebhookEvent> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends WebhookEvent> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends WebhookEvent, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<WebhookEvent> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<WebhookEvent> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    static class FakeProviderRepository implements ProviderRepository {
        final Map<UUID, Provider> db = new HashMap<>();

        @Override public Optional<Provider> findByCode(String code) {
            return db.values().stream().filter(p -> p.getCode().equalsIgnoreCase(code)).findFirst();
        }

        @Override public Optional<Provider> findByCodeIgnoreCase(String code) {
            return findByCode(code);
        }

        @Override public <S extends Provider> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends Provider> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Provider> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<Provider> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<Provider> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(Provider entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Provider> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends Provider> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Provider> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<Provider> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Provider getOne(UUID uuid) { return db.get(uuid); }
        @Override public Provider getById(UUID uuid) { return db.get(uuid); }
        @Override public Provider getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends Provider> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Provider> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Provider> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Provider> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Provider> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Provider> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Provider, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<Provider> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<Provider> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }
}
