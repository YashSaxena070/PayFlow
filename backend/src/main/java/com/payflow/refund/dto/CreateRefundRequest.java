package com.payflow.refund.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
        @NotNull(message = "amountMinor is required")
        @Min(value = 1, message = "amountMinor must be greater than zero")
        Long amountMinor,

        @Size(max = 255, message = "reason cannot exceed 255 characters")
        String reason
) {}

