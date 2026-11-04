package com.payflow.routing.repository;

import com.payflow.routing.domain.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {
    List<RoutingRule> findAllByMerchantIdOrderByPriorityAsc(UUID merchantId);
    Optional<RoutingRule> findByIdAndMerchantId(UUID id, UUID merchantId);
}

