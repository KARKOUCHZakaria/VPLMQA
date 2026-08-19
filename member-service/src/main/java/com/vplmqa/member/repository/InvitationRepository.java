package com.vplmqa.member.repository;

import com.vplmqa.member.entity.Invitation;
import com.vplmqa.member.enumtype.InvitationStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for invitations.
 */
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /**
     * Finds invitation by token and status.
     *
     * @param token token value
     * @param status invitation status
     * @return invitation if present
     */
    Optional<Invitation> findByTokenAndStatus(String token, InvitationStatusEnum status);

    /**
     * Finds invitations by organization and status.
     *
     * @param organizationId organization id
     * @param status invitation status
     * @return invitation list
     */
    List<Invitation> findByOrganization_IdAndStatus(UUID organizationId, InvitationStatusEnum status);
}
