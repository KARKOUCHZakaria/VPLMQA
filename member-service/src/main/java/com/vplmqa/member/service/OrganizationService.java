package com.vplmqa.member.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.member.dto.OrgMemberRequest;
import com.vplmqa.member.dto.OrgMemberResponse;
import com.vplmqa.member.dto.OrganizationRequest;
import com.vplmqa.member.dto.OrganizationResponse;
import com.vplmqa.member.entity.OrgMember;
import com.vplmqa.member.entity.Organization;
import com.vplmqa.member.enumtype.OrgRoleEnum;
import com.vplmqa.member.enumtype.PlanEnum;
import com.vplmqa.member.mapper.MemberMapper;
import com.vplmqa.member.repository.OrgMemberRepository;
import com.vplmqa.member.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for organization lifecycle and membership.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final MemberMapper memberMapper;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrgMemberRepository orgMemberRepository,
                               MemberMapper memberMapper) {
        this.organizationRepository = organizationRepository;
        this.orgMemberRepository = orgMemberRepository;
        this.memberMapper = memberMapper;
    }

    /**
     * Creates an organization.
     *
     * @param request request body
     * @return response
     */
    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        Organization organization = new Organization();
        organization.setName(request.name());
        organization.setSlug(request.slug());
        organization.setPlan(request.plan() == null ? PlanEnum.FREE : request.plan());
        organization.setCreatedBy(request.createdBy());
        return memberMapper.toOrganizationResponse(organizationRepository.save(organization));
    }

    /**
     * Gets an organization.
     *
     * @param id organization id
     * @return response
     */
    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID id) {
        return memberMapper.toOrganizationResponse(findOrganization(id));
    }

    /**
     * Updates an organization.
     *
     * @param id organization id
     * @param request request body
     * @return response
     */
    @Transactional
    public OrganizationResponse update(UUID id, OrganizationRequest request) {
        Organization organization = findOrganization(id);
        organization.setName(request.name());
        organization.setSlug(request.slug());
        organization.setPlan(request.plan() == null ? organization.getPlan() : request.plan());
        return memberMapper.toOrganizationResponse(organizationRepository.save(organization));
    }

    /**
     * Adds a member to an organization.
     *
     * @param organizationId organization id
     * @param request request body
     * @return member response
     */
    @Transactional
    public OrgMemberResponse addMember(UUID organizationId, OrgMemberRequest request) {
        OrgMember member = new OrgMember();
        member.setOrganization(findOrganization(organizationId));
        member.setUserId(request.userId());
        member.setOrgRole(request.orgRole() == null ? OrgRoleEnum.MEMBER : request.orgRole());
        member.setActive(true);
        member.setInvitedBy(request.invitedBy());
        return memberMapper.toOrgMemberResponse(orgMemberRepository.save(member));
    }

    /**
     * Removes a member from an organization.
     *
     * @param organizationId organization id
     * @param userId user id
     */
    @Transactional
    public void removeMember(UUID organizationId, UUID userId) {
        orgMemberRepository.findByOrganization_IdAndUserId(organizationId, userId)
                .ifPresent(orgMemberRepository::delete);
    }

    /**
     * Changes the role of a member.
     *
     * @param organizationId organization id
     * @param userId user id
     * @param orgRole new role
     * @return member response
     */
    @Transactional
    public OrgMemberResponse changeMemberRole(UUID organizationId, UUID userId, OrgRoleEnum orgRole) {
        OrgMember member = orgMemberRepository.findByOrganization_IdAndUserId(organizationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Organization member not found"));
        member.setOrgRole(orgRole);
        return memberMapper.toOrgMemberResponse(orgMemberRepository.save(member));
    }

    /**
     * Returns members for an organization.
     *
     * @param organizationId organization id
     * @return list of members
     */
    @Transactional(readOnly = true)
    public List<OrgMemberResponse> getMembers(UUID organizationId) {
        return orgMemberRepository.findByOrganization_Id(organizationId).stream().map(memberMapper::toOrgMemberResponse).toList();
    }

    private Organization findOrganization(UUID id) {
        return organizationRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    }
}
