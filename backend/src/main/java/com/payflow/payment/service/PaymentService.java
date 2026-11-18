package com.payflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.idempotency.domain.IdempotencyRecord;
import com.payflow.idempotency.service.IdempotencyService;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.outbox.service.OutboxService;
import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.CreatePaymentRequest;
import com.payflow.payment.dto.PaymentAttemptResponse;
import com.payflow.payment.dto.PaymentResponse;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.provider.domain.Provider;
import com.payflow.provider.port.PaymentProviderPort;
import com.payflow.provider.service.ProviderRegistry;
import com.payflow.routing.dto.RoutingDecision;
import com.payflow.routing.service.RoutingService;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final MerchantRepository merchantRepository;
    private final RoutingService routingService;
    private final ProviderRegistry providerRegistry;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            MerchantRepository merchantRepository,
            RoutingService routingService,
            ProviderRegistry providerRegistry,
            IdempotencyService idempotencyService,
            OutboxService outboxService,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.merchantRepository = merchantRepository;
        this.routingService = routingService;
        this.providerRegistry = providerRegistry;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse createPayment(UUID merchantId, String idempotencyKey, CreatePaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PayflowException(ErrorCode.INVALID_REQUEST, "Idempotency-Key header is required");
        }

        String canonical = String.format("%s:%d:%s:%s:%s",
                request.orderId(),
                request.amountMinor(),
                request.currency().toUpperCase(),
                request.paymentMethod().toUpperCase(),
                request.testScenario() != null ? request.testScenario() : "");
        String requestHash = IdempotencyService.computeHash(canonical);

        // 1. Check Idempotency Store
        Optional<IdempotencyRecord> cachedOpt = idempotencyService.checkIdempotency(
                merchantId, "CREATE_PAYMENT", idempotencyKey, requestHash);

        if (cachedOpt.isPresent()) {
            try {
                return objectMapper.readValue(cachedOpt.get().getResponseBodySafe(), PaymentResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached idempotency response, falling back to db lookup", e);
                return getPayment(merchantId, cachedOpt.get().getResourceId());
            }
        }

        // 2. Fetch Merchant
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Merchant not found: " + merchantId));

        // 3. Create Payment entity in CREATED status
        Payment payment = new Payment(
                UUID.randomUUID(),
                merchant,
                request.orderId(),
                request.amountMinor(),
                request.currency(),
                request.paymentMethod(),
                idempotencyKey,
                requestHash
        );
        payment = paymentRepository.save(payment);

        // 4. Select Route via Routing Engine with Dynamic Fallback Cascade
        RoutingDecision routingDecision = routingService.selectProvider(
                merchantId, payment.getCurrency(), payment.getPaymentMethod(), payment.getAmountMinor(), request.preferredProvider());

        List<Provider> cascadeChain = new ArrayList<>();
        cascadeChain.add(routingDecision.selectedProvider());
        if (routingDecision.fallbackChain() != null) {
            for (Provider p : routingDecision.fallbackChain()) {
                if (!p.getId().equals(routingDecision.selectedProvider().getId())) {
                    cascadeChain.add(p);
                }
            }
        }

        boolean isAutoFailover = "AUTO_FAILOVER".equalsIgnoreCase(request.testScenario())
                || "FAILOVER".equalsIgnoreCase(request.testScenario())
                || (request.orderId() != null && request.orderId().contains("SIM_FAILOVER"));

        PaymentProviderPort.InitiatePaymentResult finalResult = null;
        PaymentAttempt lastAttempt = null;
        Provider finalProvider = cascadeChain.get(0);
        int maxAttempts = Math.min(cascadeChain.size(), 3);

        for (int attemptNum = 1; attemptNum <= maxAttempts; attemptNum++) {
            Provider currentProvider = cascadeChain.get(attemptNum - 1);
            finalProvider = currentProvider;

            PaymentAttempt attempt = new PaymentAttempt(
                    UUID.randomUUID(),
                    payment,
                    currentProvider,
                    attemptNum,
                    payment.getAmountMinor()
            );
            attempt.setStatus(AttemptStatus.SUBMITTED);
            attempt = paymentAttemptRepository.save(attempt);
            payment.addAttempt(attempt);
            lastAttempt = attempt;

            // In AUTO_FAILOVER scenario:
            // Attempt #1 is injected with a 502/Provider error to demonstrate outage resilience.
            // Attempt #2 executes normally with SUCCESS to rescue the payment.
            String attemptScenario = request.testScenario();
            if (isAutoFailover) {
                attemptScenario = (attemptNum == 1) ? "5XX" : "SUCCESS";
            }

            PaymentProviderPort adapter = providerRegistry.getProvider(currentProvider.getCode());
            PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmountMinor(),
                    payment.getCurrency(),
                    payment.getPaymentMethod(),
                    request.returnUrl(),
                    attemptScenario
            );

            PaymentProviderPort.InitiatePaymentResult result;
            try {
                result = adapter.initiatePayment(command);
            } catch (Exception ex) {
                log.error("Provider communication failure on attempt #{} with {}", attemptNum, currentProvider.getCode(), ex);
                result = new PaymentProviderPort.InitiatePaymentResult(
                        null,
                        AttemptStatus.FAILED,
                        "GATEWAY_UNAVAILABLE",
                        "Provider " + currentProvider.getCode() + " returned error: " + ex.getMessage(),
                        null,
                        "Gateway Error"
                );
            }

            finalResult = result;
            attempt.setProviderReference(result.providerReference());
            attempt.setStatus(result.status());
            attempt.setFailureCode(result.failureCode());
            attempt.setFailureMessageSafe(result.failureMessageSafe());
            if (result.status().isTerminal()) {
                attempt.setCompletedAt(Instant.now());
            }
            paymentAttemptRepository.save(attempt);

            // If Attempt succeeded or requires customer 3DS authentication, halt cascade:
            if (result.status() == AttemptStatus.SUCCEEDED || result.status() == AttemptStatus.REQUIRES_ACTION) {
                log.info("Payment attempt #{} on provider [{}] resolved with status [{}]",
                        attemptNum, currentProvider.getCode(), result.status());
                break;
            }

            // If ambiguous timeout on a standard timeout test (AC-06 strictness):
            if (result.status() == AttemptStatus.UNKNOWN && !isAutoFailover) {
                log.warn("Attempt #{} on [{}] timed out with ambiguous status. Halting cascade for reconciliation safety.",
                        attemptNum, currentProvider.getCode());
                break;
            }

            // If attempt failed and backup providers exist, log failover transition:
            if (attemptNum < maxAttempts) {
                Provider nextProvider = cascadeChain.get(attemptNum);
                log.warn("Attempt #{} on [{}] failed [{}]. Triggering automatic smart failover cascade to backup gateway [{}]...",
                        attemptNum, currentProvider.getCode(), result.failureCode(), nextProvider.getCode());
            }
        }

        // 8. Update Payment Status based on latest attempt outcome
        switch (finalResult.status()) {
            case SUCCEEDED -> payment.setStatus(PaymentStatus.SUCCEEDED);
            case FAILED -> payment.setStatus(PaymentStatus.FAILED);
            case REQUIRES_ACTION -> payment.setStatus(PaymentStatus.REQUIRES_ACTION);
            case UNKNOWN, RECONCILIATION_REQUIRED, PENDING -> payment.setStatus(PaymentStatus.PENDING);
            default -> payment.setStatus(PaymentStatus.PROCESSING);
        }
        payment = paymentRepository.save(payment);

        // 9. Atomic Outbox Event
        outboxService.recordEvent(
                "Payment",
                payment.getId(),
                "PAYMENT_ATTEMPT_COMPLETED",
                Map.of(
                        "paymentId", payment.getId().toString(),
                        "status", payment.getStatus().name(),
                        "attemptId", lastAttempt.getId().toString(),
                        "attemptStatus", lastAttempt.getStatus().name(),
                        "attemptsCount", payment.getAttempts().size(),
                        "provider", finalProvider.getCode(),
                        "amountMinor", payment.getAmountMinor(),
                        "currency", payment.getCurrency()
                )
        );

        // 10. Record in Idempotency Store
        PaymentResponse response = PaymentResponse.from(payment, finalResult.redirectUrl(), finalResult.metadata());
        try {
            String responseJson = objectMapper.writeValueAsString(response);
            idempotencyService.recordSuccess(
                    merchantId,
                    "CREATE_PAYMENT",
                    idempotencyKey,
                    requestHash,
                    payment.getId(),
                    200,
                    responseJson
            );
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize payment response for idempotency caching", e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID merchantId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> searchPayments(
            UUID merchantId,
            PaymentStatus status,
            String currency,
            String orderId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable) {
        if (status == null && (currency == null || currency.isBlank())
                && (orderId == null || orderId.isBlank()) && fromDate == null && toDate == null) {
            return paymentRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                    .map(PaymentResponse::from);
        }
        String statusStr = status != null ? status.name() : null;
        String currencyStr = (currency != null && !currency.isBlank()) ? currency.trim() : null;
        String orderIdStr = (orderId != null && !orderId.isBlank()) ? orderId.trim() : null;
        return paymentRepository.searchPayments(merchantId, statusStr, currencyStr, orderIdStr, fromDate, toDate, pageable)
                .map(PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public List<PaymentAttemptResponse> getAttempts(UUID merchantId, UUID paymentId) {
        // Enforce tenant scoping
        paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));

        return paymentAttemptRepository.findAllByPaymentIdOrderByAttemptNumberAsc(paymentId)
                .stream()
                .map(PaymentAttemptResponse::from)
                .toList();
    }

    @Transactional
    public PaymentResponse syncPaymentStatus(UUID merchantId, UUID paymentId) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));

        List<PaymentAttempt> attempts = paymentAttemptRepository.findAllByPaymentIdOrderByAttemptNumberAsc(paymentId);
        if (!attempts.isEmpty()) {
            PaymentAttempt latestAttempt = attempts.get(attempts.size() - 1);
            if (latestAttempt.getProviderReference() != null && latestAttempt.getProvider() != null) {
                PaymentProviderPort adapter = providerRegistry.getProvider(latestAttempt.getProvider().getCode());
                PaymentProviderPort.ProviderPaymentStatus providerStatus = adapter.getPaymentStatus(latestAttempt.getProviderReference());
                if (providerStatus != null && providerStatus.status() != null && providerStatus.status() != latestAttempt.getStatus()) {
                    latestAttempt.setStatus(providerStatus.status());
                    if (providerStatus.status().isTerminal()) {
                        latestAttempt.setCompletedAt(Instant.now());
                    }
                    paymentAttemptRepository.save(latestAttempt);

                    switch (providerStatus.status()) {
                        case SUCCEEDED -> payment.setStatus(PaymentStatus.SUCCEEDED);
                        case FAILED -> payment.setStatus(PaymentStatus.FAILED);
                        default -> {}
                    }
                    payment = paymentRepository.save(payment);
                }
            }
        }
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse failoverPayment(UUID merchantId, UUID paymentId, String reason) {
        Payment payment = paymentRepository.findByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return PaymentResponse.from(payment);
        }

        List<PaymentAttempt> existingAttempts = paymentAttemptRepository.findAllByPaymentIdOrderByAttemptNumberAsc(paymentId);
        int nextAttemptNum = existingAttempts.size() + 1;

        // Mark latest attempt as failed if still active or requires action
        if (!existingAttempts.isEmpty()) {
            PaymentAttempt latest = existingAttempts.get(existingAttempts.size() - 1);
            if (!latest.getStatus().isTerminal()) {
                latest.setStatus(AttemptStatus.FAILED);
                latest.setFailureCode("FAILOVER_TRIGGERED");
                latest.setFailureMessageSafe(reason != null && !reason.isBlank() ? reason : "Gateway payment failed or was declined. Cascaded to backup gateway.");
                latest.setCompletedAt(Instant.now());
                paymentAttemptRepository.save(latest);
            }
        }

        // Determine fallback cascade chain from routing engine
        RoutingDecision routingDecision = routingService.selectProvider(
                merchantId, payment.getCurrency(), payment.getPaymentMethod(), payment.getAmountMinor(), null);

        List<Provider> fullChain = new ArrayList<>();
        fullChain.add(routingDecision.selectedProvider());
        if (routingDecision.fallbackChain() != null) {
            for (Provider p : routingDecision.fallbackChain()) {
                if (!p.getId().equals(routingDecision.selectedProvider().getId())) {
                    fullChain.add(p);
                }
            }
        }

        // Find attempted provider codes
        Set<String> attemptedCodes = existingAttempts.stream()
                .map(a -> a.getProvider().getCode().toLowerCase())
                .collect(Collectors.toSet());

        Provider nextProvider = null;
        for (Provider p : fullChain) {
            if (!attemptedCodes.contains(p.getCode().toLowerCase())) {
                nextProvider = p;
                break;
            }
        }

        if (nextProvider == null) {
            log.warn("No further backup providers available in cascade chain for payment {}", payment.getId());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return PaymentResponse.from(payment);
        }

        log.info("Executing automatic smart failover attempt #{} for payment {} on backup gateway [{}]",
                nextAttemptNum, payment.getId(), nextProvider.getCode());

        PaymentAttempt newAttempt = new PaymentAttempt(
                UUID.randomUUID(),
                payment,
                nextProvider,
                nextAttemptNum,
                payment.getAmountMinor()
        );
        newAttempt.setStatus(AttemptStatus.SUBMITTED);
        newAttempt = paymentAttemptRepository.save(newAttempt);
        payment.addAttempt(newAttempt);

        PaymentProviderPort adapter = providerRegistry.getProvider(nextProvider.getCode());
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                null,
                "REAL"
        );

        PaymentProviderPort.InitiatePaymentResult result;
        try {
            result = adapter.initiatePayment(command);
        } catch (Exception ex) {
            log.error("Failover provider {} failed on payment {}", nextProvider.getCode(), payment.getId(), ex);
            result = new PaymentProviderPort.InitiatePaymentResult(
                    null,
                    AttemptStatus.FAILED,
                    "GATEWAY_UNAVAILABLE",
                    ex.getMessage(),
                    null,
                    "Gateway Error"
            );
        }

        newAttempt.setProviderReference(result.providerReference());
        newAttempt.setStatus(result.status());
        newAttempt.setFailureCode(result.failureCode());
        newAttempt.setFailureMessageSafe(result.failureMessageSafe());
        if (result.status().isTerminal()) {
            newAttempt.setCompletedAt(Instant.now());
        }
        paymentAttemptRepository.save(newAttempt);

        switch (result.status()) {
            case SUCCEEDED -> payment.setStatus(PaymentStatus.SUCCEEDED);
            case FAILED -> payment.setStatus(PaymentStatus.FAILED);
            case REQUIRES_ACTION -> payment.setStatus(PaymentStatus.REQUIRES_ACTION);
            default -> payment.setStatus(PaymentStatus.PENDING);
        }
        payment = paymentRepository.save(payment);

        return PaymentResponse.from(payment, result.redirectUrl(), result.metadata());
    }
}
