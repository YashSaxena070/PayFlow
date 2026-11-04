package com.payflow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.repository.IdempotencyRecordRepository;
import com.payflow.idempotency.service.IdempotencyService;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.outbox.domain.OutboxEvent;
import com.payflow.outbox.repository.OutboxEventRepository;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.service.PaymentService;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.routing.dto.RoutingDecision;
import com.payflow.routing.service.RoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentServiceTest {

    private FakePaymentRepository paymentRepository;
    private FakePaymentAttemptRepository attemptRepository;
    private FakeMerchantRepository merchantRepository;
    private FakeIdempotencyRecordRepository idempotencyRecordRepository;
    private FakeOutboxEventRepository outboxEventRepository;

    private PaymentService paymentService;
    private UUID merchantId;
    private Provider testProvider;
    private TestProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Acme Corp", "ACTIVE");

        testProvider = new Provider(UUID.randomUUID(), "STRIPE", "Stripe Simulator", true);
        adapter = new TestProviderAdapter();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(adapter));

        paymentRepository = new FakePaymentRepository();
        attemptRepository = new FakePaymentAttemptRepository();
        merchantRepository = new FakeMerchantRepository();
        merchantRepository.save(merchant);

        idempotencyRecordRepository = new FakeIdempotencyRecordRepository();
        outboxEventRepository = new FakeOutboxEventRepository();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        IdempotencyService idempotencyService = new IdempotencyService(idempotencyRecordRepository, merchantRepository, 86400);
        OutboxService outboxService = new OutboxService(outboxEventRepository, objectMapper);

        // Dummy RoutingService that always picks testProvider
        RoutingService routingService = new RoutingService(null, null, providerRegistry, null) {
            @Override
            public RoutingDecision selectProvider(UUID mId, String currency, String paymentMethod, Long amountMinor) {
                return new RoutingDecision(testProvider, "PRIORITY", "Selected test provider", 1);
            }
        };

        paymentService = new PaymentService(
                paymentRepository,
                attemptRepository,
                merchantRepository,
                routingService,
                providerRegistry,
                idempotencyService,
                outboxService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Payment success flow marks attempt and payment SUCCEEDED and creates outbox event")
    void testSuccessfulPaymentFlow() {
        adapter.setNextResult(new PaymentProviderPort.InitiatePaymentResult(
                "ch_12345", AttemptStatus.SUCCEEDED, null, null, null, "Authorized"
        ));

        CreatePaymentRequest request = new CreatePaymentRequest("order-001", 5000L, "USD", "CARD", null, null);
        String key = "key-success-1";

        PaymentResponse response = paymentService.createPayment(merchantId, key, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCEEDED, response.status());
        assertEquals("order-001", response.orderId());
        assertEquals(5000L, response.amountMinor());
        assertEquals(1, response.attempts().size());
        assertEquals(AttemptStatus.SUCCEEDED, response.attempts().get(0).status());
        assertEquals("ch_12345", response.attempts().get(0).providerReference());

        // Verify Outbox Event
        assertEquals(1, outboxEventRepository.events.size());
        assertEquals("Payment", outboxEventRepository.events.get(0).getAggregateType());
        assertEquals("PAYMENT_ATTEMPT_COMPLETED", outboxEventRepository.events.get(0).getEventType());

        // Verify Idempotency Record persisted
        assertEquals(1, idempotencyRecordRepository.db.size());
    }

    @Test
    @DisplayName("Idempotency replay returns cached response for identical payload")
    void testIdempotencyReplay() {
        adapter.setNextResult(new PaymentProviderPort.InitiatePaymentResult(
                "ch_replay_1", AttemptStatus.SUCCEEDED, null, null, null, "Authorized"
        ));

        CreatePaymentRequest request = new CreatePaymentRequest("order-replay", 1000L, "USD", "CARD", null, null);
        String key = "key-replay";

        PaymentResponse first = paymentService.createPayment(merchantId, key, request);
        assertNotNull(first);

        // Second call with same key and same body
        PaymentResponse second = paymentService.createPayment(merchantId, key, request);
        assertNotNull(second);
        assertEquals(first.id(), second.id());
        assertEquals(first.orderId(), second.orderId());
        assertEquals(1, paymentRepository.payments.size(), "Should NOT create a new payment in DB");
    }

    @Test
    @DisplayName("Idempotency conflict (409) thrown when payload differs for same key")
    void testIdempotencyConflictOnMismatchedPayload() {
        adapter.setNextResult(new PaymentProviderPort.InitiatePaymentResult(
                "ch_conflict_1", AttemptStatus.SUCCEEDED, null, null, null, "Authorized"
        ));

        CreatePaymentRequest request1 = new CreatePaymentRequest("order-conflict-1", 1000L, "USD", "CARD", null, null);
        String key = "key-conflict";

        paymentService.createPayment(merchantId, key, request1);

        // Different amount with same idempotency key
        CreatePaymentRequest request2 = new CreatePaymentRequest("order-conflict-1", 2000L, "USD", "CARD", null, null);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                paymentService.createPayment(merchantId, key, request2));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-06 & AC-08: Ambiguous timeout transitions payment to PENDING, never FAILED, and does not retry")
    void testAmbiguousTimeoutTransitionsToPendingNeverFailed() {
        adapter.setNextResult(new PaymentProviderPort.InitiatePaymentResult(
                null, AttemptStatus.UNKNOWN, "GATEWAY_TIMEOUT", "Connection timed out waiting for provider response", null, null
        ));

        CreatePaymentRequest request = new CreatePaymentRequest("order-timeout", 7500L, "USD", "CARD", null, "TIMEOUT");
        String key = "key-timeout";

        PaymentResponse response = paymentService.createPayment(merchantId, key, request);

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING, response.status(), "Ambiguous outcome MUST be PENDING, never FAILED!");
        assertEquals(1, response.attempts().size());
        assertEquals(AttemptStatus.UNKNOWN, response.attempts().get(0).status());
        assertEquals(1, paymentRepository.payments.size());
        assertEquals(1, attemptRepository.attempts.size(), "Must NOT perform blind cross-provider retry!");
    }

    static class TestProviderAdapter implements PaymentProviderPort {
        private InitiatePaymentResult nextResult;

        public void setNextResult(InitiatePaymentResult nextResult) {
            this.nextResult = nextResult;
        }

        @Override public String getProviderCode() { return "STRIPE"; }
        @Override public ProviderCapabilities capabilities() {
            return new ProviderCapabilities(List.of("USD"), List.of("CARD"), true, true, true);
        }
        @Override public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) {
            return nextResult != null ? nextResult : new InitiatePaymentResult(
                    "ref_default", AttemptStatus.SUCCEEDED, null, null, null, "Success"
            );
        }
        @Override public ProviderPaymentStatus getPaymentStatus(String providerReference) { return null; }
        @Override public ProviderRefundResult refund(ProviderRefundCommand command) { return null; }
        @Override public boolean verifyWebhook(Map<String, String> headers, String rawBody) { return true; }
        @Override public ParsedWebhookEvent parseWebhook(String rawBody) { return null; }
    }

    static class FakePaymentRepository implements PaymentRepository {
        final Map<UUID, Payment> payments = new HashMap<>();

        @Override public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
            Payment p = payments.get(id);
            if (p != null && p.getMerchant().getId().equals(merchantId)) return Optional.of(p);
            return Optional.empty();
        }

        @Override public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return payments.values().stream()
                    .filter(p -> p.getMerchant().getId().equals(merchantId) && p.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override public Page<Payment> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable) {
            List<Payment> list = payments.values().stream()
                    .filter(p -> p.getMerchant().getId().equals(merchantId))
                    .toList();
            return new PageImpl<>(list, pageable, list.size());
        }

        @Override public Page<Payment> searchPayments(UUID merchantId, String status, String currency, String orderId, Instant fromDate, Instant toDate, Pageable pageable) {
            List<Payment> list = payments.values().stream()
                    .filter(p -> p.getMerchant().getId().equals(merchantId))
                    .toList();
            return new PageImpl<>(list, pageable, list.size());
        }

        @Override public List<Payment> findStalePayments(Instant staleThreshold) { return List.of(); }
        @Override public List<Object[]> aggregateMetricsByStatus(UUID merchantId, Instant fromDate, Instant toDate) { return List.of(); }

        @Override public <S extends Payment> S save(S entity) {
            payments.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends Payment> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Payment> findById(UUID uuid) { return Optional.ofNullable(payments.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return payments.containsKey(uuid); }
        @Override public List<Payment> findAll() { return new ArrayList<>(payments.values()); }
        @Override public List<Payment> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return payments.size(); }
        @Override public void deleteById(UUID uuid) { payments.remove(uuid); }
        @Override public void delete(Payment entity) { payments.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Payment> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { payments.clear(); }
        @Override public void flush() {}
        @Override public <S extends Payment> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Payment> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<Payment> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Payment getOne(UUID uuid) { return payments.get(uuid); }
        @Override public Payment getById(UUID uuid) { return payments.get(uuid); }
        @Override public Payment getReferenceById(UUID uuid) { return payments.get(uuid); }
        @Override public <S extends Payment> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Payment> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Payment> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Payment> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Payment> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Payment> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Payment, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<Payment> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<Payment> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    static class FakePaymentAttemptRepository implements PaymentAttemptRepository {
        final Map<UUID, PaymentAttempt> attempts = new HashMap<>();

        @Override public List<PaymentAttempt> findAllByPaymentIdOrderByAttemptNumberAsc(UUID paymentId) {
            return attempts.values().stream()
                    .filter(a -> a.getPayment().getId().equals(paymentId))
                    .sorted(Comparator.comparingInt(PaymentAttempt::getAttemptNumber))
                    .toList();
        }

        @Override public Optional<PaymentAttempt> findByProviderReference(String providerReference) {
            return attempts.values().stream().filter(a -> providerReference.equals(a.getProviderReference())).findFirst();
        }

        @Override public Optional<PaymentAttempt> findByPaymentIdAndAttemptNumber(UUID paymentId, int attemptNumber) {
            return attempts.values().stream().filter(a -> a.getPayment().getId().equals(paymentId) && a.getAttemptNumber() == attemptNumber).findFirst();
        }

        @Override public List<PaymentAttempt> findStaleAttempts(Instant staleThreshold) { return List.of(); }
        @Override public List<Object[]> aggregateMetricsByProvider(UUID merchantId, Instant fromDate, Instant toDate) { return List.of(); }

        @Override public <S extends PaymentAttempt> S save(S entity) {
            attempts.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends PaymentAttempt> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<PaymentAttempt> findById(UUID uuid) { return Optional.ofNullable(attempts.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return attempts.containsKey(uuid); }
        @Override public List<PaymentAttempt> findAll() { return new ArrayList<>(attempts.values()); }
        @Override public List<PaymentAttempt> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return attempts.size(); }
        @Override public void deleteById(UUID uuid) { attempts.remove(uuid); }
        @Override public void delete(PaymentAttempt entity) { attempts.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends PaymentAttempt> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { attempts.clear(); }
        @Override public void flush() {}
        @Override public <S extends PaymentAttempt> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends PaymentAttempt> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<PaymentAttempt> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public PaymentAttempt getOne(UUID uuid) { return attempts.get(uuid); }
        @Override public PaymentAttempt getById(UUID uuid) { return attempts.get(uuid); }
        @Override public PaymentAttempt getReferenceById(UUID uuid) { return attempts.get(uuid); }
        @Override public <S extends PaymentAttempt> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PaymentAttempt> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PaymentAttempt> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends PaymentAttempt> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends PaymentAttempt> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PaymentAttempt> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PaymentAttempt, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<PaymentAttempt> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<PaymentAttempt> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    static class FakeMerchantRepository implements MerchantRepository {
        final Map<UUID, Merchant> db = new HashMap<>();

        @Override public Optional<Merchant> findByName(String name) {
            return db.values().stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst();
        }

        @Override public <S extends Merchant> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends Merchant> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Merchant> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<Merchant> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<Merchant> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(Merchant entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Merchant> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends Merchant> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Merchant> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<Merchant> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Merchant getOne(UUID uuid) { return db.get(uuid); }
        @Override public Merchant getById(UUID uuid) { return db.get(uuid); }
        @Override public Merchant getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends Merchant> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Merchant> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Merchant> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Merchant> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Merchant> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Merchant> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Merchant, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<Merchant> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<Merchant> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    static class FakeIdempotencyRecordRepository implements IdempotencyRecordRepository {
        final Map<UUID, IdempotencyRecord> db = new HashMap<>();

        @Override public Optional<IdempotencyRecord> findByMerchantIdAndOperationAndIdempotencyKey(UUID merchantId, String operation, String key) {
            return db.values().stream()
                    .filter(r -> r.getMerchant().getId().equals(merchantId) && r.getOperation().equals(operation) && r.getIdempotencyKey().equals(key))
                    .findFirst();
        }

        @Override public <S extends IdempotencyRecord> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends IdempotencyRecord> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<IdempotencyRecord> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<IdempotencyRecord> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<IdempotencyRecord> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(IdempotencyRecord entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends IdempotencyRecord> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends IdempotencyRecord> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends IdempotencyRecord> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<IdempotencyRecord> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public IdempotencyRecord getOne(UUID uuid) { return db.get(uuid); }
        @Override public IdempotencyRecord getById(UUID uuid) { return db.get(uuid); }
        @Override public IdempotencyRecord getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends IdempotencyRecord> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IdempotencyRecord> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IdempotencyRecord> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends IdempotencyRecord> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends IdempotencyRecord> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IdempotencyRecord> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends IdempotencyRecord, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<IdempotencyRecord> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<IdempotencyRecord> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    static class FakeOutboxEventRepository implements OutboxEventRepository {
        final List<OutboxEvent> events = new ArrayList<>();

        @Override public List<OutboxEvent> findUnpublishedEvents(Pageable pageable) { return events; }

        @Override public <S extends OutboxEvent> S save(S entity) {
            events.add(entity);
            return entity;
        }

        @Override public <S extends OutboxEvent> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<OutboxEvent> findById(UUID uuid) { return Optional.empty(); }
        @Override public boolean existsById(UUID uuid) { return false; }
        @Override public List<OutboxEvent> findAll() { return events; }
        @Override public List<OutboxEvent> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return events.size(); }
        @Override public void deleteById(UUID uuid) {}
        @Override public void delete(OutboxEvent entity) {}
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends OutboxEvent> entities) {}
        @Override public void deleteAll() { events.clear(); }
        @Override public void flush() {}
        @Override public <S extends OutboxEvent> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends OutboxEvent> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<OutboxEvent> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public OutboxEvent getOne(UUID uuid) { return null; }
        @Override public OutboxEvent getById(UUID uuid) { return null; }
        @Override public OutboxEvent getReferenceById(UUID uuid) { return null; }
        @Override public <S extends OutboxEvent> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxEvent> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxEvent> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxEvent> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxEvent> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxEvent> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends OutboxEvent, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<OutboxEvent> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<OutboxEvent> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }
}
