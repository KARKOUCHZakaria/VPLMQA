package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.PlanEnum;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request payload for organizations.
 *
 * @param name organization name
 * @param slug unique slug
 * @param plan subscription plan
 * @param createdBy creator id
 */
public record OrganizationRequest(
        @NotBlank String name,
        @NotBlank String slug,
        PlanEnum plan,
        UUID createdBy
) {
}
