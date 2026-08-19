package com.vplmqa.member.event;

import java.util.UUID;

/**
 * Event payload consumed from project.created.
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
