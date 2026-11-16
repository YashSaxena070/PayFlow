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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

        // 4. Select Route via Routing Engine
        RoutingDecision routingDecision = routingService.selectProvider(
                merchantId, payment.getCurrency(), payment.getPaymentMethod(), payment.getAmountMinor(), request.preferredProvider());
        Provider provider = routingDecision.selectedProvider();

        // 5. Create Payment Attempt (attempt #1)
        PaymentAttempt attempt = new PaymentAttempt(
                UUID.randomUUID(),
                payment,
                provider,
                1,
                payment.getAmountMinor()
        );
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt = paymentAttemptRepository.save(attempt);
        payment.addAttempt(attempt);

        // 6. Execute through Provider Adapter
        PaymentProviderPort adapter = providerRegistry.getProvider(provider.getCode());
        PaymentProviderPort.InitiatePaymentCommand command = new PaymentProviderPort.InitiatePaymentCommand(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                request.returnUrl(),
                request.testScenario()
        );

        PaymentProviderPort.InitiatePaymentResult result;
        try {
            result = adapter.initiatePayment(command);
        } catch (Exception ex) {
            log.error("Unhandled exception communicating with provider {}", provider.getCode(), ex);
            result = new PaymentProviderPort.InitiatePaymentResult(
                    null,
                    AttemptStatus.UNKNOWN,
                    "PROVIDER_COMMUNICATION_ERROR",
                    "Ambiguous error contacting provider: " + ex.getMessage(),
                    null,
                    null
            );
        }

        // 7. Update Attempt
        attempt.setProviderReference(result.providerReference());
        attempt.setStatus(result.status());
        attempt.setFailureCode(result.failureCode());
        attempt.setFailureMessageSafe(result.failureMessageSafe());
        if (result.status().isTerminal()) {
            attempt.setCompletedAt(Instant.now());
        }
        paymentAttemptRepository.save(attempt);

        // 8. Update Payment Status based on strict state transition rules
        // CRITICAL RELIABILITY: UNKNOWN or ambiguous outcomes MUST transition payment to PENDING, never FAILED!
        switch (result.status()) {
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
                        "attemptId", attempt.getId().toString(),
                        "attemptStatus", attempt.getStatus().name(),
                        "provider", provider.getCode(),
                        "amountMinor", payment.getAmountMinor(),
                        "currency", payment.getCurrency()
                )
        );

        // 10. Record in Idempotency Store
        PaymentResponse response = PaymentResponse.from(payment, result.redirectUrl(), result.metadata());
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
}
