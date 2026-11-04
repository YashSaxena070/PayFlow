package com.payflow.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.audit.domain.AuditLog;
import com.payflow.audit.repository.AuditLogRepository;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "cvv", "cvc", "pan", "cardnumber", "token", "apikey", "authorization"
    );

    private final AuditLogRepository auditLogRepository;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            MerchantRepository merchantRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog logAction(UUID merchantId, UUID actorId, String action, String resourceType, String resourceId, Map<String, Object> metadata) {
        Merchant merchant = null;
        if (merchantId != null) {
            merchant = merchantRepository.findById(merchantId).orElse(null);
        }

        Map<String, Object> sanitized = sanitizeMetadata(metadata);
        String metadataJson = "{}";
        try {
            metadataJson = objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize audit metadata", e);
        }

        AuditLog auditLog = new AuditLog(
                UUID.randomUUID(),
                merchant,
                actorId != null ? actorId : UUID.randomUUID(),
                action,
                resourceType,
                resourceId,
                metadataJson,
                CorrelationIdContext.get()
        );

        return auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> listLogs(UUID merchantId, Pageable pageable) {
        if (merchantId != null) {
            return auditLogRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
        }
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null) return Collections.emptyMap();
        Map<String, Object> clean = new HashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() != null && SENSITIVE_KEYS.contains(entry.getKey().toLowerCase())) {
                clean.put(entry.getKey(), "[REDACTED]");
            } else {
                clean.put(entry.getKey(), entry.getValue());
            }
        }
        return clean;
    }
}

