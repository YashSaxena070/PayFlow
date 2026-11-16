package com.payflow.outbox.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(KafkaListener.class)
public class PaymentEventKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventKafkaConsumer.class);

    @KafkaListener(
            topics = "${payflow.kafka.topic:payflow.payments.events}",
            groupId = "${spring.kafka.consumer.group-id:payflow-core-group}",
            autoStartup = "${payflow.kafka.listener.enabled:false}"
    )
    public void consumePaymentEvent(ConsumerRecord<String, String> record) {
        log.info("⚡ [KAFKA CONSUMER] Streamed payment event received! topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());
        log.info("⚡ [KAFKA EVENT PAYLOAD]: {}", record.value());
    }
}

