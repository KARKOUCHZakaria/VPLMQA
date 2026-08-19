package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.OrgRoleEnum;

import java.time.Instant;
import java.util.UUID;

/**
 * Organization member response payload.
 */
public record OrgMemberResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        OrgRoleEnum orgRole,
        Instant joinedAt,
        boolean active,
        UUID invitedBy
) {
}
