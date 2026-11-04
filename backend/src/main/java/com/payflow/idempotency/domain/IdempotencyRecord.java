package com.payflow.idempotency.domain;

import com.payflow.merchant.domain.Merchant;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
        @UniqueConstraint(name = "uq_idempotency", columnNames = {"merchant_id", "operation", "idempotency_key"})
})
public class IdempotencyRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body_safe", nullable = false, columnDefinition = "TEXT")
    private String responseBodySafe;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public IdempotencyRecord() {}

    public IdempotencyRecord(UUID id, Merchant merchant, String operation, String idempotencyKey,
                             String requestHash, UUID resourceId, int responseStatus,
                             String responseBodySafe, Instant expiresAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.merchant = merchant;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.resourceId = resourceId;
        this.responseStatus = responseStatus;
        this.responseBodySafe = responseBodySafe;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public String getOperation() {
        return operation;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBodySafe() {
        return responseBodySafe;
    }

    public void setResponseBodySafe(String responseBodySafe) {
        this.responseBodySafe = responseBodySafe;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

