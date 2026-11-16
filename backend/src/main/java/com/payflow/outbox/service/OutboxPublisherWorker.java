package com.payflow.outbox.service;

import com.payflow.outbox.domain.OutboxEvent;
import com.payflow.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);

    private final OutboxEventRepository outboxEventRepository;
    private final String kafkaTopic;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherWorker(
            OutboxEventRepository outboxEventRepository,
            @Value("${payflow.kafka.topic:payflow.payments.events}") String kafkaTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTopic = kafkaTopic;
    }

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublishedEvents(PageRequest.of(0, 50));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pending.size());
        for (OutboxEvent event : pending) {
            try {
                // Enterprise Event-Driven Streaming: Publish Outbox payload to Apache Kafka
                if (kafkaTemplate != null) {
                    String eventKey = event.getAggregateId() != null ? event.getAggregateId().toString() : event.getId().toString();
                    kafkaTemplate.send(kafkaTopic, eventKey, event.getPayload()).whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka broker rejected event {}: {}", event.getId(), ex.getMessage());
                        } else {
                            log.info("Kafka producer streamed event [{}] to topic {} partition {} offset {}",
                                    event.getId(), kafkaTopic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
                }
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Outbox event published [{}]: type={}, aggregate={}/{}",
                        event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId());
            } catch (Exception ex) {
                event.incrementPublishAttempts();
                outboxEventRepository.save(event);
                log.error("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
            }
        }
    }
}

