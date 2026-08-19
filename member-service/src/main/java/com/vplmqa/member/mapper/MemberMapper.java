package com.vplmqa.member.mapper;

import com.vplmqa.member.dto.InvitationResponse;
import com.vplmqa.member.dto.OrgMemberResponse;
import com.vplmqa.member.dto.OrganizationResponse;
import com.vplmqa.member.dto.ProjectMemberResponse;
import com.vplmqa.member.entity.Invitation;
import com.vplmqa.member.entity.OrgMember;
import com.vplmqa.member.entity.Organization;
import com.vplmqa.member.entity.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps member entities to response DTOs.
 */
@Mapper(componentModel = "spring")
public interface MemberMapper {

    /**
     * Maps organization entity to response.
     *
     * @param organization organization entity
     * @return response
     */
    OrganizationResponse toOrganizationResponse(Organization organization);

    /**
     * Maps org member entity to response.
     *
     * @param orgMember org member entity
     * @return response
     */
    @Mapping(target = "organizationId", source = "organization.id")
    OrgMemberResponse toOrgMemberResponse(OrgMember orgMember);

    /**
     * Maps project member entity to response.
     *
     * @param projectMember project member entity
     * @return response
     */
    ProjectMemberResponse toProjectMemberResponse(ProjectMember projectMember);

    /**
     * Maps invitation entity to response.
     *
     * @param invitation invitation entity
     * @return response
     */
    @Mapping(target = "organizationId", source = "organization.id")
    InvitationResponse toInvitationResponse(Invitation invitation);
}
