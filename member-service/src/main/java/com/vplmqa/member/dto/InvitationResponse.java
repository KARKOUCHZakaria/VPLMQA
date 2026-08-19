package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.InvitationStatusEnum;
import com.vplmqa.member.enumtype.OrgRoleEnum;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitation response payload.
 */
public record InvitationResponse(
        UUID id,
        UUID organizationId,
        String email,
        String token,
        OrgRoleEnum orgRole,
        Instant expiresAt,
        Instant acceptedAt,
        UUID createdBy,
        InvitationStatusEnum status
) {
}
