package com.payflow.test.fakes;

import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

public class FakeMerchantRepository implements MerchantRepository {
    public final Map<UUID, Merchant> db = new HashMap<>();

    @Override
    public Optional<Merchant> findByName(String name) {
        return db.values().stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public <S extends Merchant> S save(S entity) {
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

