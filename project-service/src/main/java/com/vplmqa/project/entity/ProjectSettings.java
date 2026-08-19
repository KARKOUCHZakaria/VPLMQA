package com.vplmqa.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Project-level settings.
 */
@Entity
@Table(name = "project_settings")
public class ProjectSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(name = "notification_email")
    private String notificationEmail;

    @Column(name = "slack_webhook")
    private String slackWebhook;

    @Column(name = "auto_ticket_creation", nullable = false)
    private boolean autoTicketCreation;

    @Column(name = "default_severity")
    private String defaultSeverity;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications;

    @Column(name = "slack_notifications", nullable = false)
    private boolean slackNotifications;

    @Column(name = "notify_on_failure", nullable = false)
    private boolean notifyOnFailure;

    @Column(name = "weekly_summary_reports", nullable = false)
    private boolean weeklySummaryReports;

    @Column(name = "run_tests_on_deploy", nullable = false)
    private boolean runTestsOnDeploy;

    @Column(name = "ai_insights", nullable = false)
    private boolean aiInsights;

    @Column(name = "screenshot_comparison", nullable = false)
    private boolean screenshotComparison;

    @Column(name = "test_timeout_seconds", nullable = false)
    private int testTimeoutSeconds;

    @Column(name = "action_delay_ms", nullable = false)
    private int actionDelayMs;

    @Column(name = "schedule_type", nullable = false)
    private String scheduleType;

    @Column(name = "schedule_interval_hours", nullable = false)
    private int scheduleIntervalHours;

    @Column(name = "schedule_daily_time", nullable = false)
    private String scheduleDailyTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getNotificationEmail() { return notificationEmail; }
    public void setNotificationEmail(String notificationEmail) { this.notificationEmail = notificationEmail; }
    public String getSlackWebhook() { return slackWebhook; }
    public void setSlackWebhook(String slackWebhook) { this.slackWebhook = slackWebhook; }
    public boolean isAutoTicketCreation() { return autoTicketCreation; }
    public void setAutoTicketCreation(boolean autoTicketCreation) { this.autoTicketCreation = autoTicketCreation; }
    public String getDefaultSeverity() { return defaultSeverity; }
    public void setDefaultSeverity(String defaultSeverity) { this.defaultSeverity = defaultSeverity; }
    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public boolean isSlackNotifications() { return slackNotifications; }
    public void setSlackNotifications(boolean slackNotifications) { this.slackNotifications = slackNotifications; }
    public boolean isNotifyOnFailure() { return notifyOnFailure; }
    public void setNotifyOnFailure(boolean notifyOnFailure) { this.notifyOnFailure = notifyOnFailure; }
    public boolean isWeeklySummaryReports() { return weeklySummaryReports; }
    public void setWeeklySummaryReports(boolean weeklySummaryReports) { this.weeklySummaryReports = weeklySummaryReports; }
    public boolean isRunTestsOnDeploy() { return runTestsOnDeploy; }
    public void setRunTestsOnDeploy(boolean runTestsOnDeploy) { this.runTestsOnDeploy = runTestsOnDeploy; }
    public boolean isAiInsights() { return aiInsights; }
    public void setAiInsights(boolean aiInsights) { this.aiInsights = aiInsights; }
    public boolean isScreenshotComparison() { return screenshotComparison; }
    public void setScreenshotComparison(boolean screenshotComparison) { this.screenshotComparison = screenshotComparison; }
    public int getTestTimeoutSeconds() { return testTimeoutSeconds; }
    public void setTestTimeoutSeconds(int testTimeoutSeconds) { this.testTimeoutSeconds = testTimeoutSeconds; }
    public int getActionDelayMs() { return actionDelayMs; }
    public void setActionDelayMs(int actionDelayMs) { this.actionDelayMs = actionDelayMs; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public int getScheduleIntervalHours() { return scheduleIntervalHours; }
    public void setScheduleIntervalHours(int scheduleIntervalHours) { this.scheduleIntervalHours = scheduleIntervalHours; }
    public String getScheduleDailyTime() { return scheduleDailyTime; }
    public void setScheduleDailyTime(String scheduleDailyTime) { this.scheduleDailyTime = scheduleDailyTime; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
