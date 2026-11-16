package com.payflow.idempotency.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.repository.IdempotencyRecordRepository;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final MerchantRepository merchantRepository;
    private final long ttlSeconds;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            MerchantRepository merchantRepository,
            @Value("${payflow.idempotency.ttl-seconds:86400}") long ttlSeconds) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.merchantRepository = merchantRepository;
        this.ttlSeconds = ttlSeconds;
    }

    public static String computeHash(String canonicalJson) {
        if (canonicalJson == null) canonicalJson = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> checkIdempotency(UUID merchantId, String operation, String key, String requestHash) {
        if (key == null || key.isBlank()) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Idempotency-Key header is required for this operation");
        }

        String redisKey = "idemp:" + merchantId + ":" + operation + ":" + key;

        // 1. High-Performance Redis Distributed Cache Check
        if (redisTemplate != null) {
            try {
                String cachedHash = redisTemplate.opsForValue().get(redisKey);
                if (cachedHash != null) {
                    if (!cachedHash.equals(requestHash)) {
                        log.warn("Redis idempotency conflict for merchant={}, key={}: hash mismatch", merchantId, key);
                        throw new PayflowException(ErrorCode.IDEMPOTENCY_CONFLICT, "Key was already used with a different request payload");
                    }
                    String cachedBody = redisTemplate.opsForValue().get(redisKey + ":resp");
                    String cachedStatusStr = redisTemplate.opsForValue().get(redisKey + ":status");
                    if (cachedBody != null) {
                        int status = cachedStatusStr != null ? Integer.parseInt(cachedStatusStr) : 200;
                        log.info("Redis cache HIT for idempotency key={}. Replaying response in <1ms without DB lookup.", key);
                        IdempotencyRecord record = new IdempotencyRecord(
                                UUID.randomUUID(), null, operation, key, cachedHash, null, status, cachedBody, Instant.now().plusSeconds(ttlSeconds)
                        );
                        return Optional.of(record);
                    }
                }

                // Acquire distributed atomic lock to prevent concurrent double-spend race condition
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        "lock:" + merchantId + ":" + key, requestHash, Duration.ofSeconds(30)
                );
                if (Boolean.FALSE.equals(acquired)) {
                    log.warn("Concurrent duplicate request detected via Redis lock for key={}", key);
                    throw new PayflowException(ErrorCode.IDEMPOTENCY_CONFLICT, "A concurrent request with this Idempotency-Key is currently in flight.");
                }
            } catch (PayflowException pfe) {
                throw pfe;
            } catch (Exception ex) {
                log.debug("Redis idempotency check bypassed due to connection state: {}", ex.getMessage());
            }
        }

        // 2. Database Fallback & System of Record
        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository
                .findByMerchantIdAndOperationAndIdempotencyKey(merchantId, operation, key);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency conflict for merchant={}, op={}, key={}: hashes differ (incoming={}, existing={})",
                        merchantId, operation, key, requestHash, existing.getRequestHash());
                throw new PayflowException(ErrorCode.IDEMPOTENCY_CONFLICT, "Key was already used with a different request");
            }
            log.info("Idempotent request matched in DB for merchant={}, op={}, key={}. Replaying response.", merchantId, operation, key);
            return Optional.of(existing);
        }

        return Optional.empty();
    }

    @Transactional
    public IdempotencyRecord recordSuccess(UUID merchantId, String operation, String key, String requestHash,
                                          UUID resourceId, int status, String safeBody) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Merchant not found"));

        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID(),
                merchant,
                operation,
                key,
                requestHash,
                resourceId,
                status,
                safeBody,
                expiresAt
        );

        IdempotencyRecord saved;
        try {
            saved = idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition: another thread committed with same key
            log.warn("Concurrent idempotency insert detected for key={}. Resolving race.", key);
            IdempotencyRecord existing = idempotencyRecordRepository
                    .findByMerchantIdAndOperationAndIdempotencyKey(merchantId, operation, key)
                    .orElseThrow(() -> ex);

            if (!existing.getRequestHash().equals(requestHash)) {
                throw new PayflowException(ErrorCode.IDEMPOTENCY_CONFLICT, "Key was already used with a different request");
            }
            saved = existing;
        }

        // Populate Redis cache with TTL and release atomic lock
        if (redisTemplate != null) {
            try {
                String redisKey = "idemp:" + merchantId + ":" + operation + ":" + key;
                redisTemplate.opsForValue().set(redisKey, requestHash, Duration.ofSeconds(ttlSeconds));
                redisTemplate.opsForValue().set(redisKey + ":resp", safeBody, Duration.ofSeconds(ttlSeconds));
                redisTemplate.opsForValue().set(redisKey + ":status", String.valueOf(status), Duration.ofSeconds(ttlSeconds));
                redisTemplate.delete("lock:" + merchantId + ":" + key);
                log.info("Stored idempotency record in Redis distributed cache: key={}, ttl={}s", redisKey, ttlSeconds);
            } catch (Exception ex) {
                log.debug("Could not cache idempotency record in Redis: {}", ex.getMessage());
            }
        }

        return saved;
    }
}

