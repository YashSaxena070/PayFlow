package com.payflow.identity;

import com.payflow.identity.domain.ApiCredential;
import com.payflow.identity.repository.ApiCredentialRepository;
import com.payflow.identity.service.ApiKeyService;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyServiceUnitTest {

    private FakeApiCredentialRepository credentialRepository;
    private FakeMerchantRepository merchantRepository;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        credentialRepository = new FakeApiCredentialRepository();
        merchantRepository = new FakeMerchantRepository();
        apiKeyService = new ApiKeyService(credentialRepository, merchantRepository);
    }

    @Test
    @DisplayName("Should generate high-entropy API key and persist only SHA-256 hash")
    void testCreateApiKey() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Acme Test", "ACTIVE");
        merchantRepository.save(merchant);

        ApiKeyService.GeneratedApiKey result = apiKeyService.createApiKey(merchantId, "payments:write", null);

        assertNotNull(result);
        assertNotNull(result.rawSecretKey());
        assertTrue(result.rawSecretKey().startsWith("pf_live_"));
        assertEquals("payments:write", result.scopes());

        ApiCredential saved = credentialRepository.findById(result.id()).orElseThrow();
        assertEquals(result.keyPrefix(), saved.getKeyPrefix());
        assertNotEquals(result.rawSecretKey(), saved.getSecretHash());
        assertEquals(ApiKeyService.hashKey(result.rawSecretKey()), saved.getSecretHash());
    }

    @Test
    @DisplayName("Should authenticate valid API key via hash lookup")
    void testAuthenticateApiKey() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Acme Test", "ACTIVE");
        merchantRepository.save(merchant);

        ApiKeyService.GeneratedApiKey created = apiKeyService.createApiKey(merchantId, "payments:read", null);

        Optional<ApiCredential> authenticated = apiKeyService.authenticateApiKey(created.rawSecretKey());
        assertTrue(authenticated.isPresent());
        assertEquals(merchantId, authenticated.get().getMerchant().getId());
    }

    @Test
    @DisplayName("Should reject revoked API key")
    void testRevokedApiKey() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Acme Test", "ACTIVE");
        merchantRepository.save(merchant);

        ApiKeyService.GeneratedApiKey created = apiKeyService.createApiKey(merchantId, "payments:read", null);
        apiKeyService.revokeApiKey(merchantId, created.id());

        Optional<ApiCredential> authenticated = apiKeyService.authenticateApiKey(created.rawSecretKey());
        assertFalse(authenticated.isPresent());
    }

    // Lightweight in-memory test doubles avoiding ByteBuddy dynamic agents
    static class FakeMerchantRepository implements MerchantRepository {
        private final Map<UUID, Merchant> db = new HashMap<>();

        @Override public Optional<Merchant> findById(UUID id) { return Optional.ofNullable(db.get(id)); }
        @Override public <S extends Merchant> S save(S entity) { db.put(entity.getId(), entity); return entity; }
        @Override public Optional<Merchant> findByName(String name) { return db.values().stream().filter(m -> m.getName().equals(name)).findFirst(); }
        @Override public long count() { return db.size(); }
        @Override public List<Merchant> findAll() { return new ArrayList<>(db.values()); }
        @Override public boolean existsById(UUID id) { return db.containsKey(id); }
        @Override public void deleteById(UUID id) { db.remove(id); }
        @Override public void delete(Merchant entity) { db.remove(entity.getId()); }
        @Override public void deleteAll(Iterable<? extends Merchant> entities) {}
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll() { db.clear(); }
        @Override public List<Merchant> findAllById(Iterable<UUID> ids) { return Collections.emptyList(); }
        @Override public <S extends Merchant> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void flush() {}
        @Override public <S extends Merchant> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Merchant> List<S> saveAllAndFlush(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void deleteAllInBatch(Iterable<Merchant> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public Merchant getOne(UUID id) { return db.get(id); }
        @Override public Merchant getById(UUID id) { return db.get(id); }
        @Override public Merchant getReferenceById(UUID id) { return db.get(id); }
        @Override public <S extends Merchant> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends Merchant> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends Merchant> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends Merchant> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends Merchant> long count(Example<S> example) { return 0; }
        @Override public <S extends Merchant> boolean exists(Example<S> example) { return false; }
        @Override public <S extends Merchant, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<Merchant> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<Merchant> findAll(Pageable pageable) { return Page.empty(); }
    }

    static class FakeApiCredentialRepository implements ApiCredentialRepository {
        private final Map<UUID, ApiCredential> db = new HashMap<>();

        @Override public Optional<ApiCredential> findById(UUID id) { return Optional.ofNullable(db.get(id)); }
        @Override public <S extends ApiCredential> S save(S entity) { db.put(entity.getId(), entity); return entity; }
        @Override public Optional<ApiCredential> findBySecretHash(String secretHash) {
            return db.values().stream().filter(c -> c.getSecretHash().equals(secretHash)).findFirst();
        }
        @Override public List<ApiCredential> findAllByMerchantId(UUID merchantId) {
            return db.values().stream().filter(c -> c.getMerchant().getId().equals(merchantId)).toList();
        }
        @Override public List<ApiCredential> findAllByMerchantIdAndRevokedAtIsNull(UUID merchantId) {
            return db.values().stream().filter(c -> c.getMerchant().getId().equals(merchantId) && c.getRevokedAt() == null).toList();
        }
        @Override public long count() { return db.size(); }
        @Override public List<ApiCredential> findAll() { return new ArrayList<>(db.values()); }
        @Override public boolean existsById(UUID id) { return db.containsKey(id); }
        @Override public void deleteById(UUID id) { db.remove(id); }
        @Override public void delete(ApiCredential entity) { db.remove(entity.getId()); }
        @Override public void deleteAll(Iterable<? extends ApiCredential> entities) {}
        @Override public void deleteAllById(Iterable<? extends UUID> ids) {}
        @Override public void deleteAll() { db.clear(); }
        @Override public List<ApiCredential> findAllById(Iterable<UUID> ids) { return Collections.emptyList(); }
        @Override public <S extends ApiCredential> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void flush() {}
        @Override public <S extends ApiCredential> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ApiCredential> List<S> saveAllAndFlush(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public void deleteAllInBatch(Iterable<ApiCredential> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public ApiCredential getOne(UUID id) { return db.get(id); }
        @Override public ApiCredential getById(UUID id) { return db.get(id); }
        @Override public ApiCredential getReferenceById(UUID id) { return db.get(id); }
        @Override public <S extends ApiCredential> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends ApiCredential> List<S> findAll(Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends ApiCredential> List<S> findAll(Example<S> example, Sort sort) { return Collections.emptyList(); }
        @Override public <S extends ApiCredential> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends ApiCredential> long count(Example<S> example) { return 0; }
        @Override public <S extends ApiCredential> boolean exists(Example<S> example) { return false; }
        @Override public <S extends ApiCredential, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<ApiCredential> findAll(Sort sort) { return Collections.emptyList(); }
        @Override public Page<ApiCredential> findAll(Pageable pageable) { return Page.empty(); }
    }
}
