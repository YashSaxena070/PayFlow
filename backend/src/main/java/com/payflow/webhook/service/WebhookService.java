package com.payflow.webhook.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.repository.ProviderRepository;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.webhook.domain.WebhookEvent;
import com.payflow.webhook.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final ProviderRepository providerRepository;
    private final ProviderRegistry providerRegistry;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    public WebhookService(
            WebhookEventRepository webhookEventRepository,
            ProviderRepository providerRepository,
            ProviderRegistry providerRegistry,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentRepository paymentRepository,
            OutboxService outboxService) {
        this.webhookEventRepository = webhookEventRepository;
        this.providerRepository = providerRepository;
        this.providerRegistry = providerRegistry;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public WebhookEvent processWebhook(String providerCode, Map<String, String> headers, String rawBody) {
        Provider provider = providerRepository.findByCodeIgnoreCase(providerCode)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Provider not found: " + providerCode));

        PaymentProviderPort adapter = providerRegistry.getProvider(providerCode);

        // 1. Signature Verification
        boolean isValid = adapter.verifyWebhook(headers, rawBody);
        if (!isValid) {
            log.warn("Invalid webhook signature for provider: {}", providerCode);
            WebhookEvent invalidEvent = new WebhookEvent(
                    UUID.randomUUID(),
                    provider,
                    "invalid_sig_" + UUID.randomUUID(),
                    "unknown",
                    false,
                    "[REDACTED_INVALID_SIGNATURE]",
                    "FAILED"
            );
            webhookEventRepository.save(invalidEvent);
            throw new PayflowException(ErrorCode.WEBHOOK_SIGNATURE_INVALID, "Invalid webhook signature");
        }

        // 2. Parse Event
        PaymentProviderPort.ParsedWebhookEvent parsed = adapter.parseWebhook(rawBody);
        if (parsed == null || parsed.providerEventId() == null) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Unable to parse provider webhook event");
        }

        // 3. Deduplication Check (provider_id, provider_event_id)
        Optional<WebhookEvent> existingOpt = webhookEventRepository.findByProviderIdAndProviderEventId(
                provider.getId(), parsed.providerEventId());

        if (existingOpt.isPresent()) {
            log.info("Duplicate webhook event received for provider: {}, eventId: {}. Returning 200 without reprocessing.",
                    providerCode, parsed.providerEventId());
            return existingOpt.get();
        }

        // 4. Save initial event record
        WebhookEvent event = new WebhookEvent(
                UUID.randomUUID(),
                provider,
                parsed.providerEventId(),
                parsed.eventType() != null ? parsed.eventType() : "unknown",
                true,
                rawBody,
                "RECEIVED"
        );

        try {
            event = webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate race
            log.warn("Concurrent duplicate webhook race detected for eventId: {}", parsed.providerEventId());
            return webhookEventRepository.findByProviderIdAndProviderEventId(provider.getId(), parsed.providerEventId())
                    .orElse(event);
        }

        // 5. Apply state transitions to Payment and Attempt if referenced
        if (parsed.providerPaymentReference() != null && !parsed.providerPaymentReference().isBlank()) {
            Optional<PaymentAttempt> attemptOpt = paymentAttemptRepository.findByProviderReference(parsed.providerPaymentReference());
            if (attemptOpt.isPresent()) {
                PaymentAttempt attempt = attemptOpt.get();
                Payment payment = attempt.getPayment();

                if (parsed.normalizedStatus() != null) {
                    attempt.setStatus(parsed.normalizedStatus());
                    if (parsed.normalizedStatus().isTerminal()) {
                        attempt.setCompletedAt(Instant.now());
                    }
                    if (parsed.failureCode() != null) {
                        attempt.setFailureCode(parsed.failureCode());
                    }
                    if (parsed.failureMessageSafe() != null) {
                        attempt.setFailureMessageSafe(parsed.failureMessageSafe());
                    }
                    paymentAttemptRepository.save(attempt);

                    // Update payment status
                    switch (parsed.normalizedStatus()) {
                        case SUCCEEDED -> payment.setStatus(PaymentStatus.SUCCEEDED);
                        case FAILED -> payment.setStatus(PaymentStatus.FAILED);
                        case REQUIRES_ACTION -> payment.setStatus(PaymentStatus.REQUIRES_ACTION);
                        case UNKNOWN, RECONCILIATION_REQUIRED, PENDING -> payment.setStatus(PaymentStatus.PENDING);
                    }
                    paymentRepository.save(payment);

                    // Emit Outbox event
                    outboxService.recordEvent(
                            "Payment",
                            payment.getId(),
                            "WEBHOOK_PROCESSED",
                            Map.of(
                                    "paymentId", payment.getId().toString(),
                                    "status", payment.getStatus().name(),
                                    "providerEventId", parsed.providerEventId(),
                                    "attemptStatus", attempt.getStatus().name()
                            )
                    );
                }
            } else {
                log.warn("Webhook references providerPaymentReference: {} but no matching payment attempt found",
                        parsed.providerPaymentReference());
            }
        }

        event.setProcessingStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        return webhookEventRepository.save(event);
    }

    @Transactional
    public WebhookEvent replayWebhook(UUID webhookId) {
        WebhookEvent event = webhookEventRepository.findById(webhookId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Webhook event not found"));

        event.setProcessingStatus("REPLAYED");
        event.setProcessedAt(Instant.now());
        return webhookEventRepository.save(event);
    }
}

