package com.vplmqa.analytics.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class AnalyticsEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsEventConsumer.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public AnalyticsEventConsumer(EventService eventService, ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"mismatch.detected", "token.validated", "test.started", "test.failed", "test.passed", "ticket.created", "validation.required", "config.refresh", "project.created", "invitation.created"}, groupId = "analytics-service")
    public void consume(ConsumerRecord<String, String> record) {
        Map<String, Object> payload = parsePayload(record.value());
        UUID projectId = extractProjectId(payload);
        if (projectId == null) {
            logger.debug("Skipping analytics event {} because it has no projectId", record.topic());
            return;
        }
        eventService.recordEvent(record.topic(), projectId, payload);
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("value", payload);
        }
    }

    private UUID extractProjectId(Map<String, Object> payload) {
        Object rawProjectId = payload.getOrDefault("projectId", payload.get("project_id"));
        if (rawProjectId instanceof UUID uuid) {
            return uuid;
        }
        if (rawProjectId instanceof String value && !value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
