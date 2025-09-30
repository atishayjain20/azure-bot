package com.example.ingest.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaPublisherService {
    private static final Logger log = LoggerFactory.getLogger(KafkaPublisherService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaPublisherService(KafkaTemplate<String, String> kafkaTemplate,
                                 @Value("${kafka.topic.azure.pr.events:azure.devops.pr.events.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(String key, String payload) {
        kafkaTemplate.send(topic, key, payload);
        log.info("Queued webhook to Kafka topic='{}' key='{}' size={} bytes", topic, key, payload == null ? 0 : payload.length());
    }
}



