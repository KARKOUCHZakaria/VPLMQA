package com.vplmqa.notification.dto;

import java.util.UUID;

public record NotificationPreferenceRequest(
        UUID userId,
        UUID projectId,
        boolean emailEnabled,
        boolean slackEnabled,
        String slackWebhook,
        String emailAddress
) {
}
