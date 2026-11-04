package com.payflow.reporting.service;

import com.payflow.payment.domain.AttemptStatus;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.reporting.dto.AnalyticsOverviewDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AnalyticsService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public AnalyticsService(PaymentRepository paymentRepository, PaymentAttemptRepository paymentAttemptRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewDto getOverview(UUID merchantId, Instant fromDate, Instant toDate) {
        if (fromDate == null) fromDate = Instant.now().minusSeconds(86400 * 30); // 30 days default
        if (toDate == null) toDate = Instant.now();

        // 1. Query Payment Status Aggregations
        List<Object[]> statusRows = paymentRepository.aggregateMetricsByStatus(merchantId, fromDate, toDate);

        long totalVolume = 0;
        long totalCount = 0;
        long successCount = 0;
        long failedCount = 0;
        long pendingCount = 0;

        for (Object[] row : statusRows) {
            PaymentStatus status = (PaymentStatus) row[0];
            long count = ((Number) row[1]).longValue();
            long volume = ((Number) row[2]).longValue();

            totalCount += count;
            if (status == PaymentStatus.SUCCEEDED || status == PaymentStatus.PARTIALLY_REFUNDED || status == PaymentStatus.REFUNDED) {
                totalVolume += volume;
                successCount += count;
            } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
                failedCount += count;
            } else {
                pendingCount += count;
            }
        }

        double overallSuccessRate = totalCount > 0 ? ((double) successCount / totalCount) * 100.0 : 0.0;

        // 2. Query Provider Attempt Aggregations
        List<Object[]> providerRows = paymentAttemptRepository.aggregateMetricsByProvider(merchantId, fromDate, toDate);

        Map<String, long[]> providerStats = new HashMap<>(); // [total, success, failed]
        for (Object[] row : providerRows) {
            String code = (String) row[0];
            AttemptStatus attemptStatus = (AttemptStatus) row[1];
            long count = ((Number) row[2]).longValue();

            long[] stats = providerStats.computeIfAbsent(code, k -> new long[3]);
            stats[0] += count; // total
            if (attemptStatus == AttemptStatus.SUCCEEDED) {
                stats[1] += count;
            } else if (attemptStatus == AttemptStatus.FAILED) {
                stats[2] += count;
            }
        }

        List<AnalyticsOverviewDto.ProviderMetricDto> providerMetrics = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : providerStats.entrySet()) {
            long total = entry.getValue()[0];
            long success = entry.getValue()[1];
            long failed = entry.getValue()[2];
            double rate = total > 0 ? ((double) success / total) * 100.0 : 0.0;

            providerMetrics.add(new AnalyticsOverviewDto.ProviderMetricDto(
                    entry.getKey(), total, success, failed, Math.round(rate * 10.0) / 10.0
            ));
        }

        return new AnalyticsOverviewDto(
                totalVolume,
                totalCount,
                successCount,
                failedCount,
                pendingCount,
                Math.round(overallSuccessRate * 10.0) / 10.0,
                providerMetrics
        );
    }
}

