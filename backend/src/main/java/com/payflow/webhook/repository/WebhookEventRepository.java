package com.payflow.webhook.repository;

import com.payflow.webhook.domain.WebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByProviderIdAndProviderEventId(UUID providerId, String providerEventId);

    Page<WebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}

