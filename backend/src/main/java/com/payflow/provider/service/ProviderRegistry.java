package com.payflow.provider.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.provider.port.PaymentProviderPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProviderRegistry {

    private final Map<String, PaymentProviderPort> providers = new ConcurrentHashMap<>();

    public ProviderRegistry(List<PaymentProviderPort> providerAdapters) {
        for (PaymentProviderPort adapter : providerAdapters) {
            providers.put(adapter.getProviderCode().toLowerCase(), adapter);
        }
    }

    public PaymentProviderPort getProvider(String providerCode) {
        if (providerCode == null) {
            throw new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Provider code cannot be null");
        }
        PaymentProviderPort provider = providers.get(providerCode.toLowerCase());
        if (provider == null) {
            throw new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "No provider adapter registered for code: " + providerCode);
        }
        return provider;
    }

    public Optional<PaymentProviderPort> findProvider(String providerCode) {
        if (providerCode == null) return Optional.empty();
        return Optional.ofNullable(providers.get(providerCode.toLowerCase()));
    }

    public List<PaymentProviderPort> getAllProviders() {
        return List.copyOf(providers.values());
    }
}

