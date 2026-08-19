package com.vplmqa.project.event;

import java.util.UUID;

/**
 * Event emitted when a project is created.
 *
 * @param projectId project id
 * @param name project name
 * @param organizationId organization id
 * @param createdBy creator id
 */
public record ProjectCreatedEvent(
        UUID projectId,
        String name,
        UUID organizationId,
        UUID createdBy
) {
}
