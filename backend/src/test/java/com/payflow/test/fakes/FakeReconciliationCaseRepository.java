package com.payflow.test.fakes;

import com.payflow.reconciliation.domain.ReconciliationCase;
import com.payflow.reconciliation.repository.ReconciliationCaseRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

public class FakeReconciliationCaseRepository implements ReconciliationCaseRepository {
    public final Map<UUID, ReconciliationCase> db = new HashMap<>();

    @Override
    public List<ReconciliationCase> findAllByCaseStatusOrderByCreatedAtDesc(String caseStatus) {
        return db.values().stream().filter(c -> caseStatus.equalsIgnoreCase(c.getCaseStatus())).toList();
    }

    @Override
    public Page<ReconciliationCase> findAllByOrderByCreatedAtDesc(Pageable pageable) {
        List<ReconciliationCase> list = new ArrayList<>(db.values());
        return new PageImpl<>(list, pageable, list.size());
    }

    @Override
    public Optional<ReconciliationCase> findByAttemptId(UUID attemptId) {
        return db.values().stream()
                .filter(c -> c.getAttempt() != null && c.getAttempt().getId().equals(attemptId))
                .findFirst();
    }

    @Override
    public <S extends ReconciliationCase> S save(S entity) {
        db.put(entity.getId(), entity);
        return entity;
    }

    @Override public <S extends ReconciliationCase> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
    @Override public Optional<ReconciliationCase> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
    @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
    @Override public List<ReconciliationCase> findAll() { return new ArrayList<>(db.values()); }
    @Override public List<ReconciliationCase> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
    @Override public long count() { return db.size(); }
    @Override public void deleteById(UUID uuid) { db.remove(uuid); }
    @Override public void delete(ReconciliationCase entity) { db.remove(entity.getId()); }
    @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
    @Override public void deleteAll(Iterable<? extends ReconciliationCase> entities) { throw new UnsupportedOperationException(); }
    @Override public void deleteAll() { db.clear(); }
    @Override public void flush() {}
    @Override public <S extends ReconciliationCase> S saveAndFlush(S entity) { return save(entity); }
    @Override public <S extends ReconciliationCase> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
    @Override public void deleteAllInBatch(Iterable<ReconciliationCase> entities) {}
    @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
    @Override public void deleteAllInBatch() {}
    @Override public ReconciliationCase getOne(UUID uuid) { return db.get(uuid); }
    @Override public ReconciliationCase getById(UUID uuid) { return db.get(uuid); }
    @Override public ReconciliationCase getReferenceById(UUID uuid) { return db.get(uuid); }
    @Override public <S extends ReconciliationCase> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends ReconciliationCase> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends ReconciliationCase> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
    @Override public <S extends ReconciliationCase> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
    @Override public <S extends ReconciliationCase> long count(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends ReconciliationCase> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends ReconciliationCase, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
    @Override public List<ReconciliationCase> findAll(Sort sort) { throw new UnsupportedOperationException(); }
    @Override public Page<ReconciliationCase> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
}

