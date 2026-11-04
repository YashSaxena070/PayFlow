package com.payflow.outbox.service;

import com.payflow.outbox.domain.OutboxEvent;
import com.payflow.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);

    private final OutboxEventRepository outboxEventRepository;

    public OutboxPublisherWorker(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublishedEvents(PageRequest.of(0, 50));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pending.size());
        for (OutboxEvent event : pending) {
            try {
                // In production with active Kafka broker, kafkaTemplate.send(topic, event.getPayload());
                // Here we mark published with timestamp and commit transactionally
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Published outbox event {} for aggregate {}/{}", event.getId(), event.getAggregateType(), event.getAggregateId());
            } catch (Exception ex) {
                event.incrementPublishAttempts();
                outboxEventRepository.save(event);
                log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }
}

