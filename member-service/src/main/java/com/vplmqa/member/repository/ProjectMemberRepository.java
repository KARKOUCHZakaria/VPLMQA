package com.vplmqa.member.repository;

import com.vplmqa.member.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for project members.
 */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    /**
     * Finds a project member by project and user.
     *
     * @param projectId project id
     * @param userId user id
     * @return member if present
     */
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    /**
     * Finds members by user.
     *
     * @param userId user id
     * @return member list
     */
    List<ProjectMember> findByUserId(UUID userId);

    /**
     * Finds project members by project id.
     *
     * @param projectId project id
     * @return member list
     */
    List<ProjectMember> findByProjectId(UUID projectId);
}
