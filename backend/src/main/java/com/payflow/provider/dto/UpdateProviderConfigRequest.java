package com.payflow.provider.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateProviderConfigRequest(
        Boolean enabled,
        @Min(value = 1, message = "priority must be at least 1")
        Integer priority,
        @Min(value = 0, message = "weight must be at least 0")
        @Max(value = 1000, message = "weight cannot exceed 1000")
        Integer weight
) {}

