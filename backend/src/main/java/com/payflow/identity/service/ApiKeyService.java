package com.payflow.identity.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.identity.domain.ApiCredential;
import com.payflow.identity.repository.ApiCredentialRepository;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiCredentialRepository apiCredentialRepository;
    private final MerchantRepository merchantRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiCredentialRepository apiCredentialRepository, MerchantRepository merchantRepository) {
        this.apiCredentialRepository = apiCredentialRepository;
        this.merchantRepository = merchantRepository;
    }

    public record GeneratedApiKey(
            UUID id,
            String keyPrefix,
            String rawSecretKey,
            String scopes,
            Instant createdAt
    ) {}

    @Transactional
    public GeneratedApiKey createApiKey(UUID merchantId, String scopes, Instant expiresAt) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Merchant not found"));

        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);
        String rawKey = "pf_live_" + randomHex;
        String keyPrefix = rawKey.substring(0, 15) + "...";
        String secretHash = hashKey(rawKey);

        ApiCredential credential = new ApiCredential(
                UUID.randomUUID(),
                merchant,
                keyPrefix,
                secretHash,
                scopes != null && !scopes.isBlank() ? scopes : "payments:read,payments:write,refunds:write",
                expiresAt
        );

        apiCredentialRepository.save(credential);

        return new GeneratedApiKey(
                credential.getId(),
                credential.getKeyPrefix(),
                rawKey,
                credential.getScopes(),
                credential.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<ApiCredential> authenticateApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String hash = hashKey(rawKey.trim());
        return apiCredentialRepository.findBySecretHash(hash)
                .filter(ApiCredential::isActive);
    }

    @Transactional(readOnly = true)
    public List<ApiCredential> listApiKeys(UUID merchantId) {
        return apiCredentialRepository.findAllByMerchantId(merchantId);
    }

    @Transactional
    public void revokeApiKey(UUID merchantId, UUID keyId) {
        ApiCredential cred = apiCredentialRepository.findById(keyId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "API Key not found"));

        if (!cred.getMerchant().getId().equals(merchantId)) {
            throw new PayflowException(ErrorCode.FORBIDDEN, "API Key does not belong to merchant");
        }

        cred.setRevokedAt(Instant.now());
        apiCredentialRepository.save(cred);
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

