package com.vplmqa.project.dto;

import com.vplmqa.project.enumtype.ProjectStatusEnum;

import java.time.Instant;
import java.util.UUID;

/**
 * Project response payload.
 *
 * @param id project id
 * @param name project name
 * @param description description
 * @param figmaFileUrl Figma file URL
 * @param baseUrl base URL
 * @param status status
 * @param createdBy creator id
 * @param organizationId organization id
 * @param createdAt creation time
 * @param updatedAt update time
 */
public record ProjectResponse(
        UUID id,
        String name,
        String description,
        String figmaFileUrl,
        String baseUrl,
        ProjectStatusEnum status,
        UUID createdBy,
        UUID organizationId,
        Instant createdAt,
        Instant updatedAt
) {
}
