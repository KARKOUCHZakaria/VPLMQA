package com.vplmqa.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificationListeners {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListeners.class);
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationListeners(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "test.failed", groupId = "notification-service")
    public void onTestFailed(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            UUID userId = UUID.fromString((String) data.get("userId"));
            UUID projectId = UUID.fromString((String) data.get("projectId"));
            notificationService.notifyAllChannels(userId, projectId, "Test Failed", "An automated test has failed.");
        } catch (Exception e) {
            logger.error("Error processing test.failed", e);
        }
    }

    @KafkaListener(topics = "ticket.created", groupId = "notification-service")
    public void onTicketCreated(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            UUID userId = UUID.fromString((String) data.get("userId"));
            UUID projectId = UUID.fromString((String) data.get("projectId"));
            notificationService.notifyAllChannels(userId, projectId, "Ticket Created", "A new ticket was created.");
        } catch (Exception e) {
            logger.error("Error processing ticket.created", e);
        }
    }

    @KafkaListener(topics = "validation.required", groupId = "notification-service")
    public void onValidationRequired(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            UUID userId = UUID.fromString((String) data.get("userId"));
            UUID projectId = UUID.fromString((String) data.get("projectId"));
            notificationService.notifyAllChannels(userId, projectId, "Validation Required", "A component requires validation.");
        } catch (Exception e) {
            logger.error("Error processing validation.required", e);
        }
    }

    @KafkaListener(topics = "invitation.created", groupId = "notification-service")
    public void onInvitationCreated(String payload) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            UUID userId = UUID.fromString((String) data.get("userId"));
            UUID projectId = UUID.fromString((String) data.get("projectId"));
            notificationService.notifyAllChannels(userId, projectId, "Invitation", "You have been invited to a project.");
        } catch (Exception e) {
            logger.error("Error processing invitation.created", e);
        }
    }
}
