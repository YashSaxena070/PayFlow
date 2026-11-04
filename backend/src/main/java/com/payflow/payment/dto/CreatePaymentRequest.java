package com.payflow.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @NotBlank(message = "orderId is required")
        @Size(max = 100, message = "orderId cannot exceed 100 characters")
        String orderId,

        @NotNull(message = "amountMinor is required")
        @Min(value = 1, message = "amountMinor must be greater than zero")
        Long amountMinor,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        @NotBlank(message = "paymentMethod is required")
        String paymentMethod,

        String returnUrl,

        String testScenario
) {}

