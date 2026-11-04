package com.payflow.provider.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.provider.domain.MerchantProviderConfig;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.dto.ProviderDto;
import com.payflow.provider.dto.UpdateProviderConfigRequest;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.repository.MerchantProviderConfigRepository;
import com.payflow.provider.repository.ProviderRepository;
import com.payflow.provider.service.ProviderRegistry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderRepository providerRepository;
    private final MerchantProviderConfigRepository configRepository;
    private final MerchantRepository merchantRepository;
    private final ProviderRegistry providerRegistry;

    public ProviderController(
            ProviderRepository providerRepository,
            MerchantProviderConfigRepository configRepository,
            MerchantRepository merchantRepository,
            ProviderRegistry providerRegistry) {
        this.providerRepository = providerRepository;
        this.configRepository = configRepository;
        this.merchantRepository = merchantRepository;
        this.providerRegistry = providerRegistry;
    }

    private UUID getMerchantId() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }
        return merchantId;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderDto>>> getProviders() {
        UUID merchantId = getMerchantId();
        List<Provider> allProviders = providerRepository.findAll();
        List<MerchantProviderConfig> merchantConfigs = configRepository.findAllByMerchantId(merchantId);

        List<ProviderDto> dtos = allProviders.stream().map(provider -> {
            Optional<MerchantProviderConfig> configOpt = merchantConfigs.stream()
                    .filter(c -> c.getProvider().getId().equals(provider.getId()))
                    .findFirst();

            PaymentProviderPort adapter = providerRegistry.findProvider(provider.getCode()).orElse(null);
            return ProviderDto.from(provider, configOpt.orElse(null), adapter);
        }).toList();

        return ResponseEntity.ok(ApiResponse.of(dtos, CorrelationIdContext.get()));
    }

    @PatchMapping("/{providerId}/configuration")
    @Transactional
    public ResponseEntity<ApiResponse<ProviderDto>> updateConfiguration(
            @PathVariable UUID providerId,
            @Valid @RequestBody UpdateProviderConfigRequest request) {
        UUID merchantId = getMerchantId();

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found"));

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Merchant not found"));

        MerchantProviderConfig config = configRepository.findByMerchantIdAndProviderId(merchantId, providerId)
                .orElseGet(() -> new MerchantProviderConfig(UUID.randomUUID(), merchant, provider, true, 1, 100));

        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.priority() != null) {
            config.setPriority(request.priority());
        }
        if (request.weight() != null) {
            config.setWeight(request.weight());
        }

        config = configRepository.save(config);
        PaymentProviderPort adapter = providerRegistry.findProvider(provider.getCode()).orElse(null);

        return ResponseEntity.ok(ApiResponse.of(ProviderDto.from(provider, config, adapter), CorrelationIdContext.get()));
    }
}

