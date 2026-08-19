package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.ProjectRoleEnum;

import java.time.Instant;
import java.util.UUID;

/**
 * Project member response payload.
 */
public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        ProjectRoleEnum projectRole,
        Instant assignedAt,
        UUID assignedBy
) {
}
