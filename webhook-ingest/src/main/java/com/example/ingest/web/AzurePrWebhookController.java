package com.example.ingest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.ingest.service.KafkaPublisherService;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping(path = "/webhooks/azure", produces = MediaType.APPLICATION_JSON_VALUE)
public class AzurePrWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AzurePrWebhookController.class);

    private final ObjectMapper objectMapper;
    private final KafkaPublisherService kafkaPublisherService;

    public AzurePrWebhookController(ObjectMapper objectMapper,
                                    KafkaPublisherService kafkaPublisherService) {
        this.objectMapper = objectMapper;
        this.kafkaPublisherService = kafkaPublisherService;
    }

    @PostMapping(path = "/pr", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handlePullRequestCreated(
            @RequestBody String rawBody,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "Content-Type", required = false) String contentType
    ) {
        log.info("Received Azure webhook: contentType={}, userAgent={}", contentType, userAgent);
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException parseError) {
            log.warn("Invalid JSON payload from Azure webhook", parseError);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid JSON payload");
        }
        String eventType = optionalText(root, "eventType");
        if (!"git.pullrequest.created".equals(eventType)) {
            log.warn("Unexpected eventType: {}", eventType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported eventType");
        }
        JsonNode resource = root.path("resource");
        if (!resource.isObject()) {
            log.warn("Missing resource object in payload");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing resource");
        }
        JsonNode prIdNode = resource.path("pullRequestId");
        if (!prIdNode.isNumber()) {
            log.warn("Missing pullRequestId in resource");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing pullRequestId");
        }
        long prId = prIdNode.asLong();
        try {
            kafkaPublisherService.publish(String.valueOf(prId), rawBody);
        } catch (Exception e) {
            log.warn("Failed to publish webhook to Kafka for prId={}", prId, e);
        }
        log.info("Accepted PR created event for prId={} (payloadSize={} bytes)", prId, rawBody.length());
        return ResponseEntity.ok("ok");
    }

    private static String optionalText(JsonNode node, String fieldName) {
        return Optional.ofNullable(node.get(fieldName)).map(JsonNode::asText).orElse(null);
    }
}



