package com.payflow.provider.dto;

import com.payflow.provider.domain.MerchantProviderConfig;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;

import java.util.List;
import java.util.UUID;

public record ProviderDto(
        UUID id,
        String code,
        String displayName,
        boolean active,
        boolean merchantEnabled,
        int priority,
        int weight,
        List<String> supportedCurrencies,
        List<String> supportedMethods,
        boolean supports3DS,
        boolean supportsRefunds,
        boolean supportsWebhooks) {
    public static ProviderDto from(Provider provider, MerchantProviderConfig config, PaymentProviderPort adapter) {
        PaymentProviderPort.ProviderCapabilities caps = adapter != null ? adapter.capabilities() : null;
        return new ProviderDto(
                provider.getId(),
                provider.getCode(),
                provider.getDisplayName(),
                provider.isEnabled(),
                config != null && config.isEnabled(),
                config != null ? config.getPriority() : 1,
                config != null ? config.getWeight() : 100,
                caps != null ? caps.supportedCurrencies() : List.of(),
                caps != null ? caps.supportedMethods() : List.of(),
                caps != null && caps.supports3DS(),
                caps != null && caps.supportsRefunds(),
                caps != null && caps.supportsWebhooks());
    }
}
