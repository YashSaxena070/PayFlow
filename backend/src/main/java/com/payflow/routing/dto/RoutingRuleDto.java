package com.payflow.routing.dto;

import com.payflow.routing.domain.RoutingRule;

import java.time.Instant;
import java.util.UUID;

public record RoutingRuleDto(
        UUID id,
        String name,
        String strategy,
        String conditionsJson,
        int priority,
        boolean enabled,
        int version,
        Instant createdAt
) {
    public static RoutingRuleDto from(RoutingRule rule) {
        return new RoutingRuleDto(
                rule.getId(),
                rule.getName(),
                rule.getStrategy(),
                rule.getConditionsJson(),
                rule.getPriority(),
                rule.isEnabled(),
                rule.getVersion(),
                rule.getCreatedAt()
        );
    }
}

