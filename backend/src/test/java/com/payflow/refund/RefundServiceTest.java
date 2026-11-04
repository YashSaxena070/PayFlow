package com.payflow.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.repository.IdempotencyRecordRepository;
import com.payflow.idempotency.service.IdempotencyService;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.PaymentServiceTest;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.refund.domain.Refund;
import com.payflow.refund.domain.RefundStatus;
import com.payflow.refund.dto.CreateRefundRequest;
import com.payflow.refund.dto.RefundResponse;
import com.payflow.refund.repository.RefundRepository;
import com.payflow.refund.service.RefundService;
import com.payflow.test.fakes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class RefundServiceTest {

    private FakeRefundRepository refundRepository;
    private FakePaymentRepository paymentRepository;
    private FakeIdempotencyRecordRepository idempotencyRecordRepository;
    private FakeOutboxEventRepository outboxEventRepository;
    private FakeMerchantRepository merchantRepository;
    private RefundTestAdapter adapter;
    private RefundService refundService;

    private UUID merchantId;
    private Payment payment;
    private Provider provider;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Acme Corp", "ACTIVE");

        provider = new Provider(UUID.randomUUID(), "STRIPE", "Stripe Simulator", true);
        adapter = new RefundTestAdapter();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(adapter));

        refundRepository = new FakeRefundRepository();
        paymentRepository = new FakePaymentRepository();
        idempotencyRecordRepository = new FakeIdempotencyRecordRepository();
        outboxEventRepository = new FakeOutboxEventRepository();
        merchantRepository = new FakeMerchantRepository();
        merchantRepository.save(merchant);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        IdempotencyService idempotencyService = new IdempotencyService(idempotencyRecordRepository, merchantRepository, 86400);
        OutboxService outboxService = new OutboxService(outboxEventRepository, objectMapper);

        payment = new Payment(UUID.randomUUID(), merchant, "order-ref-1", 10000L, "USD", "CARD", "key-p", "hash-p");
        payment.setStatus(PaymentStatus.SUCCEEDED);

        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), payment, provider, 1, 10000L);
        attempt.setStatus(AttemptStatus.SUCCEEDED);
        attempt.setProviderReference("ch_charge_100");
        payment.addAttempt(attempt);
        paymentRepository.save(payment);

        refundService = new RefundService(
                refundRepository,
                paymentRepository,
                providerRegistry,
                idempotencyService,
                outboxService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Partial refund succeeds and updates payment to PARTIALLY_REFUNDED")
    void testSuccessfulPartialRefund() {
        adapter.setNextResult(new PaymentProviderPort.ProviderRefundResult("re_123", true, null, null));

        RefundResponse response = refundService.createRefund(
                merchantId, payment.getId(), "ref-key-1", new CreateRefundRequest(4000L, "Customer return"));

        assertNotNull(response);
        assertEquals(RefundStatus.SUCCEEDED, response.status());
        assertEquals("re_123", response.providerRefundReference());
        assertEquals(4000L, response.amountMinor());

        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(1, outboxEventRepository.events.size());
    }

    @Test
    @DisplayName("Full refund succeeds and updates payment to REFUNDED")
    void testSuccessfulFullRefund() {
        adapter.setNextResult(new PaymentProviderPort.ProviderRefundResult("re_full", true, null, null));

        RefundResponse response = refundService.createRefund(
                merchantId, payment.getId(), "ref-key-2", new CreateRefundRequest(10000L, "Total return"));

        assertNotNull(response);
        assertEquals(RefundStatus.SUCCEEDED, response.status());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    @DisplayName("AC-10: Refund exceeding available balance throws REFUND_EXCEEDS_PAYMENT_AMOUNT")
    void testRefundExceedsCapturedAmountThrows422() {
        PayflowException ex = assertThrows(PayflowException.class, () ->
                refundService.createRefund(merchantId, payment.getId(), "ref-key-3", new CreateRefundRequest(15000L, "Too much")));

        assertEquals(ErrorCode.REFUND_EXCEEDS_PAYMENT_AMOUNT, ex.getErrorCode());
        assertEquals(0, refundRepository.db.size());
    }

    @Test
    @DisplayName("Refunding a non-succeeded payment throws PAYMENT_NOT_REFUNDABLE")
    void testRefundNonSucceededPaymentThrows422() {
        payment.setStatus(PaymentStatus.FAILED);

        PayflowException ex = assertThrows(PayflowException.class, () ->
                refundService.createRefund(merchantId, payment.getId(), "ref-key-4", new CreateRefundRequest(1000L, "Failed payment")));

        assertEquals(ErrorCode.PAYMENT_NOT_REFUNDABLE, ex.getErrorCode());
    }

    static class RefundTestAdapter implements PaymentProviderPort {
        private ProviderRefundResult nextResult = new ProviderRefundResult("re_test", true, null, null);

        public void setNextResult(ProviderRefundResult nextResult) {
            this.nextResult = nextResult;
        }

        @Override public String getProviderCode() { return "STRIPE"; }
        @Override public ProviderCapabilities capabilities() {
            return new ProviderCapabilities(List.of("USD"), List.of("CARD"), true, true, true);
        }
        @Override public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) { return null; }
        @Override public ProviderPaymentStatus getPaymentStatus(String providerReference) { return null; }
        @Override public ProviderRefundResult refund(ProviderRefundCommand command) { return nextResult; }
        @Override public boolean verifyWebhook(Map<String, String> headers, String rawBody) { return true; }
        @Override public ParsedWebhookEvent parseWebhook(String rawBody) { return null; }
    }

    static class FakeRefundRepository implements RefundRepository {
        final Map<UUID, Refund> db = new HashMap<>();

        @Override public List<Refund> findAllByPaymentId(UUID paymentId) {
            return db.values().stream().filter(r -> r.getPayment().getId().equals(paymentId)).toList();
        }

        @Override public Optional<Refund> findByIdAndMerchantId(UUID id, UUID merchantId) {
            Refund r = db.get(id);
            if (r != null && r.getMerchant().getId().equals(merchantId)) return Optional.of(r);
            return Optional.empty();
        }

        @Override public Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
            return db.values().stream()
                    .filter(r -> r.getMerchant().getId().equals(merchantId) && r.getIdempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override public Page<Refund> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable) {
            List<Refund> list = db.values().stream().filter(r -> r.getMerchant().getId().equals(merchantId)).toList();
            return new PageImpl<>(list, pageable, list.size());
        }

        @Override public Long sumActiveRefundAmountByPaymentId(UUID paymentId) {
            return db.values().stream()
                    .filter(r -> r.getPayment().getId().equals(paymentId) && (r.getStatus() == RefundStatus.SUCCEEDED || r.getStatus() == RefundStatus.PENDING))
                    .mapToLong(Refund::getAmountMinor)
                    .sum();
        }

        @Override public <S extends Refund> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends Refund> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<Refund> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<Refund> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<Refund> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(Refund entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends Refund> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends Refund> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Refund> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<Refund> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Refund getOne(UUID uuid) { return db.get(uuid); }
        @Override public Refund getById(UUID uuid) { return db.get(uuid); }
        @Override public Refund getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends Refund> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Refund> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Refund> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Refund> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Refund> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Refund> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Refund, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<Refund> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<Refund> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }
}
