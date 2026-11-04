package com.payflow.routing;

import com.payflow.merchant.domain.Merchant;
import com.payflow.provider.domain.MerchantProviderConfig;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.repository.MerchantProviderConfigRepository;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.routing.domain.RoutingRule;
import com.payflow.routing.dto.RoutingDecision;
import com.payflow.routing.repository.RoutingRuleRepository;
import com.payflow.routing.service.RoutingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class RoutingServiceTest {

    private FakeMerchantProviderConfigRepository configRepo;
    private FakeRoutingRuleRepository ruleRepo;
    private CircuitBreakerRegistry cbRegistry;
    private ProviderRegistry providerRegistry;
    private RoutingService routingService;

    private UUID merchantId;
    private Provider stripeProvider;
    private Provider adyenProvider;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Test Merchant", "ACTIVE");

        stripeProvider = new Provider(UUID.randomUUID(), "STRIPE", "Stripe Simulator", true);
        adyenProvider = new Provider(UUID.randomUUID(), "ADYEN", "Adyen Simulator", true);

        configRepo = new FakeMerchantProviderConfigRepository();
        ruleRepo = new FakeRoutingRuleRepository();
        cbRegistry = CircuitBreakerRegistry.ofDefaults();

        PaymentProviderPort stripeAdapter = new DummyProviderAdapter("STRIPE", List.of("USD", "EUR"), List.of("CARD"));
        PaymentProviderPort adyenAdapter = new DummyProviderAdapter("ADYEN", List.of("USD", "INR"), List.of("CARD", "UPI"));

        providerRegistry = new ProviderRegistry(List.of(stripeAdapter, adyenAdapter));

        // Priority 1 for Stripe (weight 70), Priority 2 for Adyen (weight 30)
        configRepo.save(new MerchantProviderConfig(UUID.randomUUID(), merchant, stripeProvider, true, 1, 70));
        configRepo.save(new MerchantProviderConfig(UUID.randomUUID(), merchant, adyenProvider, true, 2, 30));

        routingService = new RoutingService(configRepo, ruleRepo, providerRegistry, cbRegistry, new Random(42));
    }

    @Test
    @DisplayName("Default priority routing selects lowest priority number")
    void testPriorityRouting() {
        RoutingDecision decision = routingService.selectProvider(merchantId, "USD", "CARD", 1000L);
        assertNotNull(decision);
        assertEquals("STRIPE", decision.selectedProvider().getCode());
        assertEquals("PRIORITY", decision.strategy());
    }

    @Test
    @DisplayName("Excludes provider when currency is unsupported")
    void testCurrencyExclusion() {
        // INR is supported by Adyen, but not Stripe
        RoutingDecision decision = routingService.selectProvider(merchantId, "INR", "CARD", 1000L);
        assertNotNull(decision);
        assertEquals("ADYEN", decision.selectedProvider().getCode());
    }

    @Test
    @DisplayName("Excludes provider when circuit breaker is open")
    void testCircuitBreakerExclusion() {
        // Open circuit breaker for stripe
        CircuitBreaker cb = cbRegistry.circuitBreaker("stripe");
        cb.transitionToOpenState();

        RoutingDecision decision = routingService.selectProvider(merchantId, "USD", "CARD", 1000L);
        assertNotNull(decision);
        assertEquals("ADYEN", decision.selectedProvider().getCode());
        assertTrue(decision.explanation().contains("circuit breaker is OPEN"));
    }

    @Test
    @DisplayName("Weighted routing respects active WEIGHTED routing rule")
    void testWeightedRouting() {
        Merchant merchant = new Merchant(merchantId, "Test Merchant", "ACTIVE");
        RoutingRule rule = new RoutingRule(UUID.randomUUID(), merchant, "Split traffic", "WEIGHTED", "{}", 1, true);
        ruleRepo.save(rule);

        RoutingDecision decision = routingService.selectProvider(merchantId, "USD", "CARD", 1000L);
        assertNotNull(decision);
        assertEquals("WEIGHTED", decision.strategy());
        assertTrue(decision.selectedProvider().getCode().equals("STRIPE") || decision.selectedProvider().getCode().equals("ADYEN"));
    }

    static class DummyProviderAdapter implements PaymentProviderPort {
        private final String code;
        private final ProviderCapabilities caps;

        DummyProviderAdapter(String code, List<String> currencies, List<String> methods) {
            this.code = code;
            this.caps = new ProviderCapabilities(currencies, methods, true, true, true);
        }

        @Override public String getProviderCode() { return code; }
        @Override public ProviderCapabilities capabilities() { return caps; }
        @Override public InitiatePaymentResult initiatePayment(InitiatePaymentCommand command) { return null; }
        @Override public ProviderPaymentStatus getPaymentStatus(String providerReference) { return null; }
        @Override public ProviderRefundResult refund(ProviderRefundCommand command) { return null; }
        @Override public boolean verifyWebhook(Map<String, String> headers, String rawBody) { return true; }
        @Override public ParsedWebhookEvent parseWebhook(String rawBody) { return null; }
    }

    static class FakeMerchantProviderConfigRepository implements MerchantProviderConfigRepository {
        private final Map<UUID, MerchantProviderConfig> db = new HashMap<>();

        @Override public List<MerchantProviderConfig> findEnabledByMerchantIdOrdered(UUID merchantId) {
            return db.values().stream()
                    .filter(c -> c.getMerchant().getId().equals(merchantId) && c.isEnabled())
                    .sorted(Comparator.comparingInt(MerchantProviderConfig::getPriority))
                    .toList();
        }

        @Override public List<MerchantProviderConfig> findAllByMerchantId(UUID merchantId) {
            return db.values().stream().filter(c -> c.getMerchant().getId().equals(merchantId)).toList();
        }

        @Override public Optional<MerchantProviderConfig> findByMerchantIdAndProviderId(UUID merchantId, UUID providerId) {
            return db.values().stream()
                    .filter(c -> c.getMerchant().getId().equals(merchantId) && c.getProvider().getId().equals(providerId))
                    .findFirst();
        }

        @Override public <S extends MerchantProviderConfig> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends MerchantProviderConfig> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<MerchantProviderConfig> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<MerchantProviderConfig> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<MerchantProviderConfig> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(MerchantProviderConfig entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends MerchantProviderConfig> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends MerchantProviderConfig> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends MerchantProviderConfig> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<MerchantProviderConfig> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public MerchantProviderConfig getOne(UUID uuid) { return db.get(uuid); }
        @Override public MerchantProviderConfig getById(UUID uuid) { return db.get(uuid); }
        @Override public MerchantProviderConfig getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends MerchantProviderConfig> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends MerchantProviderConfig> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends MerchantProviderConfig> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends MerchantProviderConfig> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends MerchantProviderConfig> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends MerchantProviderConfig> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends MerchantProviderConfig, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<MerchantProviderConfig> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<MerchantProviderConfig> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    static class FakeRoutingRuleRepository implements RoutingRuleRepository {
        private final Map<UUID, RoutingRule> db = new HashMap<>();

        @Override public Optional<RoutingRule> findByIdAndMerchantId(UUID id, UUID merchantId) {
            RoutingRule r = db.get(id);
            if (r != null && r.getMerchant().getId().equals(merchantId)) return Optional.of(r);
            return Optional.empty();
        }

        @Override public List<RoutingRule> findAllByMerchantIdOrderByPriorityAsc(UUID merchantId) {
            return db.values().stream()
                    .filter(r -> r.getMerchant().getId().equals(merchantId))
                    .sorted(Comparator.comparingInt(RoutingRule::getPriority))
                    .toList();
        }

        @Override public <S extends RoutingRule> S save(S entity) {
            db.put(entity.getId(), entity);
            return entity;
        }

        @Override public <S extends RoutingRule> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<RoutingRule> findById(UUID uuid) { return Optional.ofNullable(db.get(uuid)); }
        @Override public boolean existsById(UUID uuid) { return db.containsKey(uuid); }
        @Override public List<RoutingRule> findAll() { return new ArrayList<>(db.values()); }
        @Override public List<RoutingRule> findAllById(Iterable<UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public long count() { return db.size(); }
        @Override public void deleteById(UUID uuid) { db.remove(uuid); }
        @Override public void delete(RoutingRule entity) { db.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends RoutingRule> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { db.clear(); }
        @Override public void flush() {}
        @Override public <S extends RoutingRule> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends RoutingRule> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<RoutingRule> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public RoutingRule getOne(UUID uuid) { return db.get(uuid); }
        @Override public RoutingRule getById(UUID uuid) { return db.get(uuid); }
        @Override public RoutingRule getReferenceById(UUID uuid) { return db.get(uuid); }
        @Override public <S extends RoutingRule> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends RoutingRule> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends RoutingRule> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends RoutingRule> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends RoutingRule> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends RoutingRule> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends RoutingRule, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override public List<RoutingRule> findAll(Sort sort) { throw new UnsupportedOperationException(); }
        @Override public Page<RoutingRule> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    }
}
