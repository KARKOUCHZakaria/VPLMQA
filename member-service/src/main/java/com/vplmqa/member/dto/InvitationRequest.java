package com.vplmqa.member.dto;

import com.vplmqa.member.enumtype.OrgRoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Request payload for invitations.
 *
 * @param email invitee email
 * @param orgRole organization role
 * @param createdBy creator id
 */
public record InvitationRequest(
        @Email @NotBlank String email,
        OrgRoleEnum orgRole,
        UUID createdBy
) {
}
