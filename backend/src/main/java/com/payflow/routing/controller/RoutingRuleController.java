package com.payflow.routing.controller;

import com.payflow.common.dto.ApiResponse;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayflowException;
import com.payflow.common.tenant.TenantContext;
import com.payflow.common.tracing.CorrelationIdContext;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.repository.MerchantRepository;
import com.payflow.routing.domain.RoutingRule;
import com.payflow.routing.dto.CreateRoutingRuleRequest;
import com.payflow.routing.dto.RoutingRuleDto;
import com.payflow.routing.repository.RoutingRuleRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routing-rules")
public class RoutingRuleController {

    private final RoutingRuleRepository routingRuleRepository;
    private final MerchantRepository merchantRepository;

    public RoutingRuleController(RoutingRuleRepository routingRuleRepository, MerchantRepository merchantRepository) {
        this.routingRuleRepository = routingRuleRepository;
        this.merchantRepository = merchantRepository;
    }

    private UUID getMerchantId() {
        UUID merchantId = TenantContext.getMerchantId();
        if (merchantId == null) {
            throw new PayflowException(ErrorCode.UNAUTHORIZED, "Merchant context required");
        }
        return merchantId;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutingRuleDto>>> listRules() {
        UUID merchantId = getMerchantId();
        List<RoutingRuleDto> rules = routingRuleRepository.findAllByMerchantIdOrderByPriorityAsc(merchantId)
                .stream()
                .map(RoutingRuleDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(rules, CorrelationIdContext.get()));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<RoutingRuleDto>> createRule(@Valid @RequestBody CreateRoutingRuleRequest request) {
        UUID merchantId = getMerchantId();
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Merchant not found"));

        RoutingRule rule = new RoutingRule(
                UUID.randomUUID(),
                merchant,
                request.name(),
                request.strategy().toUpperCase(),
                request.conditionsJson() != null ? request.conditionsJson() : "{}",
                request.priority() != null ? request.priority() : 1,
                request.enabled() != null ? request.enabled() : true
        );

        rule = routingRuleRepository.save(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(RoutingRuleDto.from(rule), CorrelationIdContext.get()));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<RoutingRuleDto>> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRoutingRuleRequest request) {
        UUID merchantId = getMerchantId();

        RoutingRule rule = routingRuleRepository.findById(id)
                .filter(r -> r.getMerchant().getId().equals(merchantId))
                .orElseThrow(() -> new PayflowException(ErrorCode.RESOURCE_NOT_FOUND, "Routing rule not found"));

        rule.setName(request.name());
        rule.setStrategy(request.strategy().toUpperCase());
        if (request.conditionsJson() != null) {
            rule.setConditionsJson(request.conditionsJson());
        }
        if (request.priority() != null) {
            rule.setPriority(request.priority());
        }
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }

        rule = routingRuleRepository.save(rule);
        return ResponseEntity.ok(ApiResponse.of(RoutingRuleDto.from(rule), CorrelationIdContext.get()));
    }
}

