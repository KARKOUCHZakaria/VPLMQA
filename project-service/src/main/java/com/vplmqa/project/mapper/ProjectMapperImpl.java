package com.vplmqa.project.mapper;

import com.vplmqa.project.dto.ComponentResponse;
import com.vplmqa.project.dto.PageResponse;
import com.vplmqa.project.dto.ProjectResponse;
import com.vplmqa.project.dto.ProjectSettingsResponse;
import com.vplmqa.project.entity.Page;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.entity.ProjectSettings;
import org.springframework.stereotype.Service;

/**
 * Hand-coded mapper implementation replacing MapStruct generation.
 * MapStruct annotation processing is disabled via <proc>none</proc> to avoid
 * a Java 21 / MapStruct 1.5.x incompatibility (TypeTag UNKNOWN crash).
 */
@Service
public class ProjectMapperImpl implements ProjectMapper {

    @Override
    public ProjectResponse toProjectResponse(Project project) {
        if (project == null) {
            return null;
        }
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getFigmaFileUrl(),
                project.getBaseUrl(),
                project.getStatus(),
                project.getCreatedBy(),
                project.getOrganizationId(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    @Override
    public PageResponse toPageResponse(Page page) {
        if (page == null) {
            return null;
        }
        return new PageResponse(
                page.getId(),
                page.getProject() != null ? page.getProject().getId() : null,
                page.getName(),
                page.getUrl(),
                page.getPath(),
                page.getLastScannedAt(),
                page.getScanStatus(),
                page.getFigmaObjectPath() != null && !page.getFigmaObjectPath().isBlank() ? "FIGMA"
                        : page.getWebObjectPath() != null && !page.getWebObjectPath().isBlank() ? "WEB"
                        : "WEB",
                page.getFigmaObjectPath(),
                page.getWebObjectPath(),
                page.getCreatedAt()
        );
    }

    @Override
    public ComponentResponse toComponentResponse(com.vplmqa.project.entity.Component component) {
        if (component == null) {
            return null;
        }
        return new ComponentResponse(
                component.getId(),
                component.getPage() != null ? component.getPage().getId() : null,
                component.getCanonicalName(),
                component.getSemanticRole(),
                component.getFunctionalMeaning(),
                component.getHtmlId(),
                component.getFigmaNodeId(),
                component.getTestIdentifier(),
                component.getCssSelector(),
                component.getXpath(),
                component.getSource(),
                component.getStatus(),
                component.getCssProperties(),
                component.getBoundingBox(),
                component.getScreenshot(),
                component.getCreatedAt(),
                component.getUpdatedAt()
        );
    }

    @Override
    public ProjectSettingsResponse toSettingsResponse(ProjectSettings settings) {
        if (settings == null) {
            return null;
        }
        return new ProjectSettingsResponse(
                settings.getId(),
                settings.getProject() != null ? settings.getProject().getId() : null,
                settings.getNotificationEmail(),
                settings.getSlackWebhook(),
                settings.isAutoTicketCreation(),
                settings.getDefaultSeverity(),
                settings.isEmailNotifications(),
                settings.isSlackNotifications(),
                settings.isNotifyOnFailure(),
                settings.isWeeklySummaryReports(),
                settings.isRunTestsOnDeploy(),
                settings.isAiInsights(),
                settings.isScreenshotComparison(),
                settings.getTestTimeoutSeconds(),
                settings.getActionDelayMs(),
                settings.getScheduleType(),
                settings.getScheduleIntervalHours(),
                settings.getScheduleDailyTime(),
                settings.getCreatedAt(),
                settings.getUpdatedAt()
        );
    }
}
