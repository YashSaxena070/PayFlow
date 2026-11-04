package com.payflow.routing.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.provider.domain.MerchantProviderConfig;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.repository.MerchantProviderConfigRepository;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.routing.domain.RoutingRule;
import com.payflow.routing.dto.RoutingDecision;
import com.payflow.routing.repository.RoutingRuleRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final MerchantProviderConfigRepository merchantProviderConfigRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final ProviderRegistry providerRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Random random;

    @org.springframework.beans.factory.annotation.Autowired
    public RoutingService(
            MerchantProviderConfigRepository merchantProviderConfigRepository,
            RoutingRuleRepository routingRuleRepository,
            ProviderRegistry providerRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this(merchantProviderConfigRepository, routingRuleRepository, providerRegistry, circuitBreakerRegistry, new Random());
    }

    public RoutingService(
            MerchantProviderConfigRepository merchantProviderConfigRepository,
            RoutingRuleRepository routingRuleRepository,
            ProviderRegistry providerRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            Random random) {
        this.merchantProviderConfigRepository = merchantProviderConfigRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.providerRegistry = providerRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.random = random;
    }

    @Transactional(readOnly = true)
    public RoutingDecision selectProvider(UUID merchantId, String currency, String paymentMethod, Long amountMinor) {
        List<MerchantProviderConfig> configs = merchantProviderConfigRepository.findEnabledByMerchantIdOrdered(merchantId);

        if (configs.isEmpty()) {
            List<PaymentProviderPort> ports = providerRegistry.getAllProviders();
            if (!ports.isEmpty()) {
                configs = new ArrayList<>();
                int p = 1;
                for (PaymentProviderPort port : ports) {
                    Provider prov = new Provider(UUID.randomUUID(), port.getProviderCode(), port.getProviderCode().toUpperCase() + " Simulator", true);
                    configs.add(new MerchantProviderConfig(UUID.randomUUID(), null, prov, true, p++, 100));
                }
            } else {
                throw new PayflowException(ErrorCode.PROVIDER_UNAVAILABLE, "No enabled payment providers configured for this merchant");
            }
        }

        List<String> filterReasons = new ArrayList<>();
        List<MerchantProviderConfig> eligible = new ArrayList<>();

        for (MerchantProviderConfig config : configs) {
            Provider provider = config.getProvider();
            String code = provider.getCode().toLowerCase();

            // 1. Check Circuit Breaker
            Optional<CircuitBreaker> cbOpt = circuitBreakerRegistry.find(code);
            if (cbOpt.isPresent() && cbOpt.get().getState() == CircuitBreaker.State.OPEN) {
                filterReasons.add("Provider [" + code + "] excluded: circuit breaker is OPEN");
                continue;
            }

            // 2. Check Provider Capabilities
            Optional<PaymentProviderPort> adapterOpt = providerRegistry.findProvider(code);
            if (adapterOpt.isEmpty()) {
                filterReasons.add("Provider [" + code + "] excluded: adapter not registered");
                continue;
            }

            PaymentProviderPort.ProviderCapabilities caps = adapterOpt.get().capabilities();
            if (currency != null && !caps.supportedCurrencies().contains(currency.toUpperCase())) {
                filterReasons.add("Provider [" + code + "] excluded: currency " + currency + " not supported");
                continue;
            }
            if (paymentMethod != null && !caps.supportedMethods().contains(paymentMethod.toUpperCase())) {
                filterReasons.add("Provider [" + code + "] excluded: method " + paymentMethod + " not supported");
                continue;
            }

            eligible.add(config);
        }

        if (eligible.isEmpty()) {
            String details = String.join("; ", filterReasons);
            throw new PayflowException(ErrorCode.PROVIDER_UNAVAILABLE, "No eligible payment provider available: " + details);
        }

        // Check if custom active routing rules exist
        List<RoutingRule> rules = routingRuleRepository.findAllByMerchantIdOrderByPriorityAsc(merchantId);
        Optional<RoutingRule> activeRule = rules.stream().filter(RoutingRule::isEnabled).findFirst();

        if (activeRule.isPresent() && "WEIGHTED".equalsIgnoreCase(activeRule.get().getStrategy())) {
            MerchantProviderConfig chosen = pickWeighted(eligible);
            String explanation = String.format("Selected [%s] via weighted routing rule '%s' (weight %d/%d). %s",
                    chosen.getProvider().getCode(), activeRule.get().getName(), chosen.getWeight(),
                    totalWeight(eligible), formatFiltered(filterReasons));
            return new RoutingDecision(chosen.getProvider(), "WEIGHTED", explanation, activeRule.get().getVersion());
        }

        // Default: Deterministic Priority routing
        MerchantProviderConfig chosen = eligible.get(0);
        String explanation = String.format("Selected [%s] via priority routing (priority %d). %s",
                chosen.getProvider().getCode(), chosen.getPriority(), formatFiltered(filterReasons));

        return new RoutingDecision(chosen.getProvider(), "PRIORITY", explanation.trim(), 1);
    }

    private MerchantProviderConfig pickWeighted(List<MerchantProviderConfig> configs) {
        int total = totalWeight(configs);
        if (total <= 0) return configs.get(0);

        int randVal = random.nextInt(total);
        int running = 0;
        for (MerchantProviderConfig config : configs) {
            running += config.getWeight();
            if (randVal < running) {
                return config;
            }
        }
        return configs.get(0);
    }

    private int totalWeight(List<MerchantProviderConfig> configs) {
        return configs.stream().mapToInt(MerchantProviderConfig::getWeight).sum();
    }

    private String formatFiltered(List<String> reasons) {
        if (reasons.isEmpty()) return "";
        return "Notes: " + String.join(", ", reasons) + ".";
    }
}

