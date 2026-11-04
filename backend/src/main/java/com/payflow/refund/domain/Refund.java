package com.payflow.refund.domain;

import com.payflow.merchant.domain.Merchant;
import com.payflow.payment.domain.Payment;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RefundStatus status;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "provider_refund_reference")
    private String providerRefundReference;

    @Column(length = 255)
    private String reason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Refund() {}

    public Refund(UUID id, Payment payment, Merchant merchant, Long amountMinor, String currency,
                  String idempotencyKey, String reason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.payment = payment;
        this.merchant = merchant;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = RefundStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
        this.version = 0L;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderRefundReference() {
        return providerRefundReference;
    }

    public void setProviderRefundReference(String providerRefundReference) {
        this.providerRefundReference = providerRefundReference;
    }

    public String getReason() {
        return reason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

