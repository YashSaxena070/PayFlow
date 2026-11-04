package com.payflow.test.fakes;

import com.payflow.outbox.domain.OutboxEvent;
import com.payflow.outbox.repository.OutboxEventRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

public class FakeOutboxEventRepository implements OutboxEventRepository {
    public final List<OutboxEvent> events = new ArrayList<>();

    @Override
    public List<OutboxEvent> findUnpublishedEvents(Pageable pageable) {
        return events.stream().filter(e -> e.getPublishedAt() == null).toList();
    }

    @Override
    public <S extends OutboxEvent> S save(S entity) {
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

