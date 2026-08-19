package com.vplmqa.member.service;

import com.vplmqa.common.ApiException;
import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.member.dto.InvitationRequest;
import com.vplmqa.member.dto.InvitationResponse;
import com.vplmqa.member.entity.Invitation;
import com.vplmqa.member.entity.Organization;
import com.vplmqa.member.enumtype.InvitationStatusEnum;
import com.vplmqa.member.enumtype.OrgRoleEnum;
import com.vplmqa.member.event.ProjectCreatedEvent;
import com.vplmqa.member.mapper.MemberMapper;
import com.vplmqa.member.repository.InvitationRepository;
import com.vplmqa.member.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for invitations.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final MemberMapper memberMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InvitationService(InvitationRepository invitationRepository,
                             OrganizationRepository organizationRepository,
                             MemberMapper memberMapper,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.invitationRepository = invitationRepository;
        this.organizationRepository = organizationRepository;
        this.memberMapper = memberMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Issues an invitation.
     *
     * @param email invitee email
     * @param organizationId organization id
     * @param role role
     * @param createdBy creator id
     * @return response
     */
    @Transactional
    public InvitationResponse invite(String email, UUID organizationId, OrgRoleEnum role, UUID createdBy) {
        Organization organization = findOrganization(organizationId);
        Invitation invitation = new Invitation();
        invitation.setOrganization(organization);
        invitation.setEmail(email);
        invitation.setToken(UUID.randomUUID().toString());
        invitation.setOrgRole(role == null ? OrgRoleEnum.MEMBER : role);
        invitation.setExpiresAt(Instant.now().plusSeconds(86_400));
        invitation.setCreatedBy(createdBy);
        invitation.setStatus(InvitationStatusEnum.PENDING);
        Invitation saved = invitationRepository.save(invitation);
        kafkaTemplate.send("invitation.created", saved.getToken());
        return memberMapper.toInvitationResponse(saved);
    }

    /**
     * Accepts an invitation using a token.
     *
     * @param token invite token
     * @return response
     */
    @Transactional
    public InvitationResponse acceptInvitation(String token) {
        Invitation invitation = invitationRepository.findByTokenAndStatus(token, InvitationStatusEnum.PENDING)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid invitation token"));
        invitation.setStatus(InvitationStatusEnum.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        return memberMapper.toInvitationResponse(invitationRepository.save(invitation));
    }

    /**
     * Revokes an invitation.
     *
     * @param id invitation id
     * @return response
     */
    @Transactional
    public InvitationResponse revokeInvitation(UUID id) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));
        invitation.setStatus(InvitationStatusEnum.REVOKED);
        return memberMapper.toInvitationResponse(invitationRepository.save(invitation));
    }

    /**
     * Lists pending invitations.
     *
     * @param organizationId organization id
     * @return invitation list
     */
    @Transactional(readOnly = true)
    public List<InvitationResponse> listPending(UUID organizationId) {
        return invitationRepository.findByOrganization_IdAndStatus(organizationId, InvitationStatusEnum.PENDING)
                .stream().map(memberMapper::toInvitationResponse).toList();
    }

    private Organization findOrganization(UUID id) {
        return organizationRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Organization not found"));
    }
}
