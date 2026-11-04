package com.payflow.reporting.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.reporting.dto.AnalyticsOverviewDto;
import com.payflow.reporting.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    private UUID getMerchantId() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }
        return merchantId;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewDto>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {
        UUID merchantId = getMerchantId();
        AnalyticsOverviewDto overview = analyticsService.getOverview(merchantId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.of(overview, CorrelationIdContext.get()));
    }
}

