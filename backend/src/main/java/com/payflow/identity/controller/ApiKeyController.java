package com.payflow.identity.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.identity.domain.ApiCredential;
import com.payflow.identity.domain.UserRole;
import com.payflow.identity.dto.ApiKeyDto;
import com.payflow.identity.dto.CreateApiKeyRequest;
import com.payflow.identity.service.ApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApiKeyDto>> createApiKey(@RequestBody(required = false) CreateApiKeyRequest request) {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }
        String role = TenantContext.getRole();
        if (role != null && !UserRole.valueOf(role).canManageApiKeys()) {
            throw new PayflowException(ErrorCode.FORBIDDEN, "Only administrators can generate API credentials");
        }

        ApiKeyService.GeneratedApiKey result = apiKeyService.createApiKey(
                merchantId,
                request != null ? request.scopes() : null,
                request != null ? request.expiresAt() : null
        );

        ApiKeyDto dto = new ApiKeyDto(
                result.id(),
                result.keyPrefix(),
                result.rawSecretKey(),
                result.scopes(),
                null,
                null,
                result.createdAt(),
                true
        );

        return ResponseEntity.ok(ApiResponse.of(dto, CorrelationIdContext.get()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyDto>>> listApiKeys() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }

        List<ApiCredential> credentials = apiKeyService.listApiKeys(merchantId);
        List<ApiKeyDto> dtos = credentials.stream()
                .map(c -> new ApiKeyDto(
                        c.getId(),
                        c.getKeyPrefix(),
                        null, // Never returned on read
                        c.getScopes(),
                        c.getExpiresAt(),
                        c.getRevokedAt(),
                        c.getCreatedAt(),
                        c.isActive()
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.of(dtos, CorrelationIdContext.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable UUID id) {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }
        String role = TenantContext.getRole();
        if (role != null && !UserRole.valueOf(role).canManageApiKeys()) {
            throw new PayflowException(ErrorCode.FORBIDDEN, "Only administrators can revoke API credentials");
        }

        apiKeyService.revokeApiKey(merchantId, id);
        return ResponseEntity.ok(ApiResponse.of(null, CorrelationIdContext.get()));
    }
}

