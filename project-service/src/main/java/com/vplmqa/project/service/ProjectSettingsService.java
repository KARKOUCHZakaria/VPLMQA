package com.vplmqa.project.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.project.dto.ProjectSettingsRequest;
import com.vplmqa.project.dto.ProjectSettingsResponse;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.entity.ProjectSettings;
import com.vplmqa.project.mapper.ProjectMapper;
import com.vplmqa.project.repository.ProjectRepository;
import com.vplmqa.project.repository.ProjectSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for project settings.
 */
@Service
public class ProjectSettingsService {

    private final ProjectSettingsRepository projectSettingsRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    /**
     * Creates the service.
     *
     * @param projectSettingsRepository settings repository
     * @param projectRepository project repository
     * @param projectMapper mapper
     */
    public ProjectSettingsService(ProjectSettingsRepository projectSettingsRepository,
                                  ProjectRepository projectRepository,
                                  ProjectMapper projectMapper) {
        this.projectSettingsRepository = projectSettingsRepository;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    /**
     * Retrieves settings for a project.
     *
     * @param projectId project id
     * @return settings response
     */
    @Transactional
    public ProjectSettingsResponse getSettings(UUID projectId) {
        ProjectSettings settings = projectSettingsRepository.findByProjectId(projectId)
                .orElseGet(() -> projectSettingsRepository.save(createDefaults(projectId)));
        return projectMapper.toSettingsResponse(settings);
    }

    /**
     * Updates settings for a project.
     *
     * @param projectId project id
     * @param request request payload
     * @return settings response
     */
    @Transactional
    public ProjectSettingsResponse updateSettings(UUID projectId, ProjectSettingsRequest request) {
        ProjectSettings settings = projectSettingsRepository.findByProjectId(projectId).orElseGet(() -> createDefaults(projectId));
        settings.setNotificationEmail(request.notificationEmail());
        settings.setSlackWebhook(request.slackWebhook());
        settings.setAutoTicketCreation(request.autoTicketCreation());
        settings.setDefaultSeverity(request.defaultSeverity());
        settings.setEmailNotifications(boolValue(request.emailNotifications(), true));
        settings.setSlackNotifications(boolValue(request.slackNotifications(), false));
        settings.setNotifyOnFailure(boolValue(request.notifyOnFailure(), true));
        settings.setWeeklySummaryReports(boolValue(request.weeklySummaryReports(), true));
        settings.setRunTestsOnDeploy(boolValue(request.runTestsOnDeploy(), true));
        settings.setAiInsights(boolValue(request.aiInsights(), true));
        settings.setScreenshotComparison(boolValue(request.screenshotComparison(), true));
        settings.setTestTimeoutSeconds(intValue(request.testTimeoutSeconds(), 30, 1, 600));
        settings.setActionDelayMs(intValue(request.actionDelayMs(), 320, 0, 3000));
        settings.setScheduleType(textValue(request.scheduleType(), "daily"));
        settings.setScheduleIntervalHours(intValue(request.scheduleIntervalHours(), 1, 1, 24));
        settings.setScheduleDailyTime(textValue(request.scheduleDailyTime(), "06:00"));
        return projectMapper.toSettingsResponse(projectSettingsRepository.save(settings));
    }

    private ProjectSettings createDefaults(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        ProjectSettings settings = new ProjectSettings();
        settings.setProject(project);
        settings.setAutoTicketCreation(false);
        settings.setDefaultSeverity("MEDIUM");
        settings.setEmailNotifications(true);
        settings.setSlackNotifications(false);
        settings.setNotifyOnFailure(true);
        settings.setWeeklySummaryReports(true);
        settings.setRunTestsOnDeploy(true);
        settings.setAiInsights(true);
        settings.setScreenshotComparison(true);
        settings.setTestTimeoutSeconds(30);
        settings.setActionDelayMs(320);
        settings.setScheduleType("daily");
        settings.setScheduleIntervalHours(1);
        settings.setScheduleDailyTime("06:00");
        return settings;
    }

    private boolean boolValue(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private int intValue(Integer value, int fallback, int min, int max) {
        int number = value == null ? fallback : value;
        return Math.max(min, Math.min(max, number));
    }

    private String textValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
