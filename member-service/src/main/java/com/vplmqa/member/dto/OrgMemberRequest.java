package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.OrgRoleEnum;

import java.util.UUID;

/**
 * Request payload for org membership changes.
 *
 * @param userId user id
 * @param orgRole member role
 * @param invitedBy inviter id
 */
public record OrgMemberRequest(
        UUID userId,
        OrgRoleEnum orgRole,
        UUID invitedBy
) {
}
