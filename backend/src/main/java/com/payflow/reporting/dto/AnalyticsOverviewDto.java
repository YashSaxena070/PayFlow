package com.payflow.reporting.dto;

import java.util.List;

public record AnalyticsOverviewDto(
        long totalVolumeMinor,
        long totalTransactions,
        long successfulTransactions,
        long failedTransactions,
        long pendingTransactions,
        double successRatePercent,
        List<ProviderMetricDto> providerMetrics
) {
    public record ProviderMetricDto(
            String providerCode,
            long totalAttempts,
            long successfulAttempts,
            long failedAttempts,
            double successRatePercent
    ) {}
}

