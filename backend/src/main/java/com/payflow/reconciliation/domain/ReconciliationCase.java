package com.payflow.reconciliation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_cases")
public class ReconciliationCase {

    @Id
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private PaymentAttempt attempt;

    @Column(name = "expected_status", nullable = false, length = 50)
    private String expectedStatus;

    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Column(name = "case_status", nullable = false, length = 50)
    private String caseStatus; // OPEN, RESOLVED, AUTO_RESOLVED

    @Column(length = 50)
    private String resolution;

    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public ReconciliationCase() {}

    public ReconciliationCase(UUID id, Payment payment, PaymentAttempt attempt, String expectedStatus,
                              String providerStatus, String caseStatus, String resolutionReason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.payment = payment;
        this.attempt = attempt;
        this.expectedStatus = expectedStatus;
        this.providerStatus = providerStatus;
        this.caseStatus = caseStatus != null ? caseStatus : "OPEN";
        this.resolutionReason = resolutionReason;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    @JsonIgnore
    public Payment getPayment() {
        return payment;
    }

    @JsonIgnore
    public PaymentAttempt getAttempt() {
        return attempt;
    }

    @Transient
    public UUID getPaymentId() {
        return payment != null ? payment.getId() : null;
    }

    @Transient
    public UUID getAttemptId() {
        return attempt != null ? attempt.getId() : null;
    }

    public String getExpectedStatus() {
        return expectedStatus;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public String getCaseStatus() {
        return caseStatus;
    }

    public void setCaseStatus(String caseStatus) {
        this.caseStatus = caseStatus;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public void setResolutionReason(String resolutionReason) {
        this.resolutionReason = resolutionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}

