package com.payflow.routing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRoutingRuleRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "strategy is required")
        @Pattern(regexp = "PRIORITY|WEIGHTED", message = "strategy must be either PRIORITY or WEIGHTED")
        String strategy,

        String conditionsJson,

        @Min(value = 1, message = "priority must be at least 1")
        Integer priority,

        Boolean enabled
) {}

