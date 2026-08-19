package com.vplmqa.project.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request payload for creating or updating a project.
 *
 * @param name project name
 * @param description project description
 * @param figmaFileUrl Figma file URL
 * @param figmaTokenEncrypted encrypted Figma token
 * @param baseUrl base application URL
 * @param organizationId organization ID
 */
public record ProjectRequest(
        @NotBlank String name,
        String description,
        String figmaFileUrl,
        String figmaTokenEncrypted,
        String baseUrl,
        UUID organizationId
) {
}
