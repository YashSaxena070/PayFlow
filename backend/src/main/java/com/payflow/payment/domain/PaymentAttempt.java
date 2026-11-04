package com.payflow.payment.domain;

import com.payflow.provider.domain.Provider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_attempt", columnNames = {"payment_id", "attempt_number"})
})
public class PaymentAttempt {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "provider_reference")
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AttemptStatus status;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message_safe", length = 500)
    private String failureMessageSafe;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public PaymentAttempt() {}

    public PaymentAttempt(UUID id, Payment payment, Provider provider, int attemptNumber, Long amountMinor) {
        this.id = id != null ? id : UUID.randomUUID();
        this.payment = payment;
        this.provider = provider;
        this.attemptNumber = attemptNumber;
        this.amountMinor = amountMinor;
        this.status = AttemptStatus.CREATED;
        this.submittedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public void setStatus(AttemptStatus status) {
        this.status = status;
        if (status.isTerminal() && this.completedAt == null) {
            this.completedAt = Instant.now();
        }
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessageSafe() {
        return failureMessageSafe;
    }

    public void setFailureMessageSafe(String failureMessageSafe) {
        this.failureMessageSafe = failureMessageSafe;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

