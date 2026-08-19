package com.vplmqa.project.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Project settings response payload.
 *
 * @param id settings id
 * @param projectId project id
 * @param notificationEmail notification email
 * @param slackWebhook slack webhook
 * @param autoTicketCreation auto ticket flag
 * @param defaultSeverity default severity
 * @param createdAt creation time
 * @param updatedAt update time
 */
public record ProjectSettingsResponse(
        UUID id,
        UUID projectId,
        String notificationEmail,
        String slackWebhook,
        boolean autoTicketCreation,
        String defaultSeverity,
        boolean emailNotifications,
        boolean slackNotifications,
        boolean notifyOnFailure,
        boolean weeklySummaryReports,
        boolean runTestsOnDeploy,
        boolean aiInsights,
        boolean screenshotComparison,
        int testTimeoutSeconds,
        int actionDelayMs,
        String scheduleType,
        int scheduleIntervalHours,
        String scheduleDailyTime,
        Instant createdAt,
        Instant updatedAt
) {
}
