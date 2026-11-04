package com.payflow.reconciliation.service;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.reconciliation.domain.ReconciliationCase;
import com.payflow.reconciliation.repository.ReconciliationCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationCaseRepository caseRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderRegistry providerRegistry;
    private final OutboxService outboxService;

    public ReconciliationService(
            ReconciliationCaseRepository caseRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentRepository paymentRepository,
            ProviderRegistry providerRegistry,
            OutboxService outboxService) {
        this.caseRepository = caseRepository;
        this.attemptRepository = attemptRepository;
        this.paymentRepository = paymentRepository;
        this.providerRegistry = providerRegistry;
        this.outboxService = outboxService;
    }

    @Transactional
    public List<ReconciliationCase> scanStaleAttempts(int staleSeconds) {
        Instant threshold = Instant.now().minusSeconds(staleSeconds);
        List<PaymentAttempt> staleAttempts = attemptRepository.findStaleAttempts(threshold);
        List<ReconciliationCase> createdCases = new ArrayList<>();

        for (PaymentAttempt attempt : staleAttempts) {
            if (caseRepository.findByAttemptId(attempt.getId()).isPresent()) {
                continue;
            }

            Payment payment = attempt.getPayment();
            String providerCode = attempt.getProvider().getCode();
            PaymentProviderPort adapter = providerRegistry.getProvider(providerCode);

            PaymentProviderPort.ProviderPaymentStatus providerStatus = null;
            if (attempt.getProviderReference() != null) {
                try {
                    providerStatus = adapter.getPaymentStatus(attempt.getProviderReference());
                } catch (Exception ex) {
                    log.warn("Failed to check status for attempt {} at provider {}: {}",
                            attempt.getId(), providerCode, ex.getMessage());
                }
            }

            if (providerStatus != null && providerStatus.status().isTerminal()) {
                // Auto-resolve case
                attempt.setStatus(providerStatus.status());
                attempt.setCompletedAt(Instant.now());
                attemptRepository.save(attempt);

                if (providerStatus.status() == AttemptStatus.SUCCEEDED) {
                    payment.setStatus(PaymentStatus.SUCCEEDED);
                } else if (providerStatus.status() == AttemptStatus.FAILED) {
                    payment.setStatus(PaymentStatus.FAILED);
                }
                paymentRepository.save(payment);

                ReconciliationCase autoCase = new ReconciliationCase(
                        UUID.randomUUID(),
                        payment,
                        attempt,
                        "UNKNOWN",
                        providerStatus.status().name(),
                        "AUTO_RESOLVED",
                        "Provider confirmed terminal status: " + providerStatus.status()
                );
                autoCase.setResolution("AUTO_SYNC");
                autoCase.setResolvedAt(Instant.now());
                autoCase = caseRepository.save(autoCase);
                createdCases.add(autoCase);

                outboxService.recordEvent(
                        "Payment",
                        payment.getId(),
                        "RECONCILIATION_AUTO_RESOLVED",
                        Map.of("paymentId", payment.getId().toString(), "status", payment.getStatus().name())
                );
            } else {
                // Open reconciliation case for manual operator inspection
                String statusStr = providerStatus != null ? providerStatus.status().name() : "UNREACHABLE";
                ReconciliationCase openCase = new ReconciliationCase(
                        UUID.randomUUID(),
                        payment,
                        attempt,
                        attempt.getStatus().name(),
                        statusStr,
                        "OPEN",
                        "Attempt remained unconfirmed past stale threshold of " + staleSeconds + "s"
                );
                openCase = caseRepository.save(openCase);
                createdCases.add(openCase);
            }
        }

        return createdCases;
    }

    @Transactional
    public ReconciliationCase resolveCase(UUID caseId, String resolution, String reason) {
        ReconciliationCase recCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Reconciliation case not found"));

        recCase.setCaseStatus("RESOLVED");
        recCase.setResolution(resolution);
        recCase.setResolutionReason(reason);
        recCase.setResolvedAt(Instant.now());

        PaymentAttempt attempt = recCase.getAttempt();
        Payment payment = recCase.getPayment();

        if (attempt != null) {
            if ("MARK_SUCCEEDED".equalsIgnoreCase(resolution)) {
                attempt.setStatus(AttemptStatus.SUCCEEDED);
                attempt.setCompletedAt(Instant.now());
                attemptRepository.save(attempt);

                payment.setStatus(PaymentStatus.SUCCEEDED);
                paymentRepository.save(payment);
            } else if ("MARK_FAILED".equalsIgnoreCase(resolution)) {
                attempt.setStatus(AttemptStatus.FAILED);
                attempt.setCompletedAt(Instant.now());
                attemptRepository.save(attempt);

                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        }

        recCase = caseRepository.save(recCase);

        outboxService.recordEvent(
                "Payment",
                payment.getId(),
                "RECONCILIATION_RESOLVED",
                Map.of("caseId", recCase.getId().toString(), "resolution", resolution, "reason", reason != null ? reason : "")
        );

        return recCase;
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationCase> listCases(Pageable pageable) {
        return caseRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}

