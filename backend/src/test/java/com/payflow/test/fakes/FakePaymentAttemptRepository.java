package com.payflow.test.fakes;

import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.repository.PaymentAttemptRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public class FakePaymentAttemptRepository implements PaymentAttemptRepository {
    public final Map<UUID, PaymentAttempt> attempts = new HashMap<>();

    @Override
    public List<PaymentAttempt> findAllByPaymentIdOrderByAttemptNumberAsc(UUID paymentId) {
        return attempts.values().stream()
                .filter(a -> a.getPayment().getId().equals(paymentId))
                .sorted(Comparator.comparingInt(PaymentAttempt::getAttemptNumber))
                .toList();
    }

    @Override
    public Optional<PaymentAttempt> findByProviderReference(String providerReference) {
        return attempts.values().stream().filter(a -> providerReference.equals(a.getProviderReference())).findFirst();
    }

    @Override
    public Optional<PaymentAttempt> findByPaymentIdAndAttemptNumber(UUID paymentId, int attemptNumber) {
        return attempts.values().stream().filter(a -> a.getPayment().getId().equals(paymentId) && a.getAttemptNumber() == attemptNumber).findFirst();
    }

    @Override
    public List<PaymentAttempt> findStaleAttempts(Instant staleThreshold) {
        return attempts.values().stream()
                .filter(a -> (a.getStatus() == com.payflow.payment.domain.AttemptStatus.UNKNOWN || a.getStatus() == com.payflow.payment.domain.AttemptStatus.PENDING)
                        && a.getSubmittedAt().isBefore(staleThreshold.plusMillis(1)))
                .toList();
    }
    @Override public List<Object[]> aggregateMetricsByProvider(UUID merchantId, Instant fromDate, Instant toDate) { return List.of(); }

    @Override
    public <S extends PaymentAttempt> S save(S entity) {
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
