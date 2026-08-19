package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.PlanEnum;

import java.time.Instant;
import java.util.UUID;

/**
 * Organization response payload.
 */
public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        PlanEnum plan,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
