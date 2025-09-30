package com.example.reviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class KafkaPrEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPrEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ReviewPipelineService reviewPipelineService;
    private final String topic;

    public KafkaPrEventConsumer(ObjectMapper objectMapper,
                                ReviewPipelineService reviewPipelineService,
                                @Value("${kafka.topic.azure.pr.events:azure.devops.pr.events.v1}") String topic) {
        this.objectMapper = objectMapper;
        this.reviewPipelineService = reviewPipelineService;
        this.topic = topic;
    }

    @KafkaListener(topics = "#{'${kafka.topic.azure.pr.events:azure.devops.pr.events.v1}'}", groupId = "reviewer-service")
    public void onMessage(ConsumerRecord<String, String> record, @Payload String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode resource = root.path("resource");
            String repoId = resource.path("repository").path("id").asText(null);
            long prId = resource.path("pullRequestId").asLong(-1);
            String baseBranch = resource.path("targetRefName").asText(null);
            String targetBranch = resource.path("sourceRefName").asText(null);
            String baseCommitId = resource.path("lastMergeTargetCommit").path("commitId").asText(null);
            String targetCommitId = resource.path("lastMergeSourceCommit").path("commitId").asText(null);
            if (repoId == null || prId <= 0) {
                log.warn("Kafka event missing required fields; skipping. key={}, offset={}", record.key(), record.offset());
                return;
            }
            reviewPipelineService.reviewPipelineAsync(repoId, prId, baseBranch, targetBranch, baseCommitId, targetCommitId);
            log.info("Triggered review pipeline from Kafka for prId={} repoId={}", prId, repoId);
        } catch (Exception e) {
            log.warn("Failed to process Kafka PR event; key={}, offset={}", record.key(), record.offset(), e);
        }
    }
}



