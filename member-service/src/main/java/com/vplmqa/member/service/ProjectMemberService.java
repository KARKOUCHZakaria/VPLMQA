package com.vplmqa.member.service;

import com.vplmqa.common.ApiException;
import com.vplmqa.member.dto.ProjectMemberRequest;
import com.vplmqa.member.dto.ProjectMemberResponse;
import com.vplmqa.member.entity.ProjectMember;
import com.vplmqa.member.enumtype.OrgRoleEnum;
import com.vplmqa.member.enumtype.ProjectRoleEnum;
import com.vplmqa.member.event.ProjectCreatedEvent;
import com.vplmqa.member.mapper.MemberMapper;
import com.vplmqa.member.repository.ProjectMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for project membership.
 */
@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final MemberMapper memberMapper;

    public ProjectMemberService(ProjectMemberRepository projectMemberRepository, MemberMapper memberMapper) {
        this.projectMemberRepository = projectMemberRepository;
        this.memberMapper = memberMapper;
    }

    /**
     * Adds a user to a project.
     *
     * @param userId user id
     * @param projectId project id
     * @param role project role
     * @param assignedBy assigner id
     * @return response
     */
    @Transactional
    public ProjectMemberResponse addToProject(UUID userId, UUID projectId, ProjectRoleEnum role, UUID assignedBy) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId).orElseGet(ProjectMember::new);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setProjectRole(role == null ? ProjectRoleEnum.VIEWER : role);
        member.setAssignedBy(assignedBy);
        return memberMapper.toProjectMemberResponse(projectMemberRepository.save(member));
    }

    /**
     * Removes a user from a project.
     *
     * @param userId user id
     * @param projectId project id
     */
    @Transactional
    public void removeFromProject(UUID userId, UUID projectId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId).ifPresent(projectMemberRepository::delete);
    }

    /**
     * Gets project members.
     *
     * @param projectId project id
     * @return member list
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId).stream().map(memberMapper::toProjectMemberResponse).toList();
    }

    /**
     * Gets projects for a user.
     *
     * @param userId user id
     * @return member list
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getUserProjects(UUID userId) {
        return projectMemberRepository.findByUserId(userId).stream().map(memberMapper::toProjectMemberResponse).toList();
    }

    /**
     * Checks access for a user and role.
     *
     * @param userId user id
     * @param projectId project id
     * @param requiredRole required role
     * @return true if access exists
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(UUID userId, UUID projectId, ProjectRoleEnum requiredRole) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Access denied"));
        return member.getProjectRole().ordinal() <= requiredRole.ordinal();
    }

    /**
     * Adds a project member from project.created events.
     *
     * @param event event payload
     */
    @Transactional
    public void addProjectCreator(ProjectCreatedEvent event) {
        addToProject(event.createdBy(), event.projectId(), ProjectRoleEnum.MANAGER, event.createdBy());
    }
}
