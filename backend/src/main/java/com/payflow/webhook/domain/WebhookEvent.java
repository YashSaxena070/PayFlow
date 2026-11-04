package com.payflow.webhook.domain;

import com.payflow.provider.domain.Provider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_webhook_event", columnNames = {"provider_id", "provider_event_id"})
})
public class WebhookEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "payload_redacted", nullable = false, columnDefinition = "TEXT")
    private String payloadRedacted;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_status", nullable = false, length = 50)
    private String processingStatus;

    public WebhookEvent() {}

    public WebhookEvent(UUID id, Provider provider, String providerEventId, String eventType,
                        boolean signatureValid, String payloadRedacted, String processingStatus) {
        this.id = id != null ? id : UUID.randomUUID();
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.signatureValid = signatureValid;
        this.payloadRedacted = payloadRedacted;
        this.receivedAt = Instant.now();
        this.processingStatus = processingStatus;
    }

    public UUID getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean isSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public String getPayloadRedacted() {
        return payloadRedacted;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }
}

