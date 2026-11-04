package com.payflow.routing.dto;

import com.payflow.provider.domain.Provider;

public record RoutingDecision(
        Provider selectedProvider,
        String strategy,
        String explanation,
        int ruleVersion
) {}

