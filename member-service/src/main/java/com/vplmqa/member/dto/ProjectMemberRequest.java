package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.ProjectRoleEnum;

import java.util.UUID;

/**
 * Request payload for project membership changes.
 *
 * @param userId user id
 * @param projectRole project role
 * @param assignedBy assigner id
 */
public record ProjectMemberRequest(
        UUID userId,
        ProjectRoleEnum projectRole,
        UUID assignedBy
) {
}
