package com.payflow.webhook.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.webhook.domain.WebhookEvent;
import com.payflow.webhook.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{providerCode}")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @PathVariable String providerCode,
            @RequestHeader Map<String, String> headers,
            @RequestBody String rawBody) {
        WebhookEvent event = webhookService.processWebhook(providerCode, headers, rawBody);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "eventId", event.getProviderEventId(),
                "processingStatus", event.getProcessingStatus()
        ));
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> replayWebhook(@PathVariable UUID id) {
        WebhookEvent event = webhookService.replayWebhook(id);
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "id", event.getId().toString(),
                "processingStatus", event.getProcessingStatus(),
                "processedAt", event.getProcessedAt() != null ? event.getProcessedAt().toString() : ""
        ), CorrelationIdContext.get()));
    }
}

