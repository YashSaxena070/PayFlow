package com.payflow.test.fakes;

import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

public class FakeIdempotencyRecordRepository implements IdempotencyRecordRepository {
    public final Map<UUID, IdempotencyRecord> db = new HashMap<>();

    @Override
    public Optional<IdempotencyRecord> findByMerchantIdAndOperationAndIdempotencyKey(UUID merchantId, String operation, String key) {
        return db.values().stream()
                .filter(r -> r.getMerchant().getId().equals(merchantId) && r.getOperation().equals(operation) && r.getIdempotencyKey().equals(key))
                .findFirst();
    }

    @Override
    public <S extends IdempotencyRecord> S save(S entity) {
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

