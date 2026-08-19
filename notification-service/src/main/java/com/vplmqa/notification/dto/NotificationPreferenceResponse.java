package com.vplmqa.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        UUID userId,
        UUID projectId,
        boolean emailEnabled,
        boolean slackEnabled,
        String slackWebhook,
        String emailAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
