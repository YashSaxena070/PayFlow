package com.payflow.test.fakes;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public class FakePaymentRepository implements PaymentRepository {
    public final Map<UUID, Payment> payments = new HashMap<>();

    @Override
    public Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId) {
        Payment p = payments.get(id);
        if (p != null && p.getMerchant().getId().equals(merchantId)) return Optional.of(p);
        return Optional.empty();
    }

    @Override
    public Optional<Payment> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey) {
        return payments.values().stream()
                .filter(p -> p.getMerchant().getId().equals(merchantId) && p.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public Page<Payment> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable) {
        List<Payment> list = payments.values().stream()
                .filter(p -> p.getMerchant().getId().equals(merchantId))
                .toList();
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override
    public Page<Payment> searchPayments(UUID merchantId, String status, String currency, String orderId, Instant fromDate, Instant toDate, Pageable pageable) {
        List<Payment> list = payments.values().stream()
                .filter(p -> p.getMerchant().getId().equals(merchantId))
                .toList();
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override public List<Payment> findStalePayments(Instant staleThreshold) { return List.of(); }
    @Override public List<Object[]> aggregateMetricsByStatus(UUID merchantId, Instant fromDate, Instant toDate) { return List.of(); }

    @Override
    public <S extends Payment> S save(S entity) {
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

