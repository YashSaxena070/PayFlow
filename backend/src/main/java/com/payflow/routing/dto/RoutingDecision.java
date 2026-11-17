package com.payflow.routing.dto;

import com.payflow.provider.domain.Provider;
import java.util.List;

public record RoutingDecision(
        Provider selectedProvider,
        String strategy,
        String explanation,
        int ruleVersion,
        List<Provider> fallbackChain
) {
    public RoutingDecision(Provider selectedProvider, String strategy, String explanation, int ruleVersion) {
        this(selectedProvider, strategy, explanation, ruleVersion, List.of());
    }
}

