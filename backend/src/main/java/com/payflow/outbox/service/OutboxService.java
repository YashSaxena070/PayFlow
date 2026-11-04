package com.payflow.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.outbox.domain.OutboxEvent;
import com.payflow.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(String aggregateType, UUID aggregateId, String eventType, Object eventData) {
        try {
            String payloadJson = objectMapper.writeValueAsString(eventData);
            OutboxEvent event = new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, payloadJson);
            outboxEventRepository.save(event);
            log.debug("Recorded outbox event: {} for aggregate {}/{}", eventType, aggregateType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for {}/{}", aggregateType, aggregateId, e);
            throw new RuntimeException("Could not serialize outbox event", e);
        }
    }
}

