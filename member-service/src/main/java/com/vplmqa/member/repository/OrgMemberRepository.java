package com.vplmqa.member.repository;

import com.vplmqa.member.entity.OrgMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for organization members.
 */
public interface OrgMemberRepository extends JpaRepository<OrgMember, UUID> {

    /**
     * Finds a member in an organization by user.
     *
     * @param organizationId organization id
     * @param userId user id
     * @return member if present
     */
    Optional<OrgMember> findByOrganization_IdAndUserId(UUID organizationId, UUID userId);

    /**
     * Finds members by user.
     *
     * @param userId user id
     * @return member list
     */
    List<OrgMember> findByUserId(UUID userId);

    /**
     * Finds all members of an organization.
     *
     * @param organizationId organization id
     * @return member list
     */
    List<OrgMember> findByOrganization_Id(UUID organizationId);
}
