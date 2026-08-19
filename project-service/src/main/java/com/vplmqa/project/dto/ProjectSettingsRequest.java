package com.vplmqa.project.dto;

/**
 * Request payload for project settings.
 *
 * @param notificationEmail notification email
 * @param slackWebhook Slack webhook
 * @param autoTicketCreation auto ticket flag
 * @param defaultSeverity default severity
 */
public record ProjectSettingsRequest(
        String notificationEmail,
        String slackWebhook,
        boolean autoTicketCreation,
        String defaultSeverity,
        Boolean emailNotifications,
        Boolean slackNotifications,
        Boolean notifyOnFailure,
        Boolean weeklySummaryReports,
        Boolean runTestsOnDeploy,
        Boolean aiInsights,
        Boolean screenshotComparison,
        Integer testTimeoutSeconds,
        Integer actionDelayMs,
        String scheduleType,
        Integer scheduleIntervalHours,
        String scheduleDailyTime
) {
}
