package com.vplmqa.member.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.member.dto.ProjectMemberRequest;
import com.vplmqa.member.dto.ProjectMemberResponse;
import com.vplmqa.member.enumtype.ProjectRoleEnum;
import com.vplmqa.member.service.ProjectMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for project members.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    public ProjectMemberController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(projectMemberService.getProjectMembers(projectId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> add(@PathVariable UUID projectId,
                                                                  @RequestBody ProjectMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectMemberService.addToProject(request.userId(), projectId, request.projectRole(), request.assignedBy())));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID projectId, @PathVariable UUID userId) {
        projectMemberService.removeFromProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Project member removed"));
    }

    @GetMapping("/access-check")
    public ResponseEntity<ApiResponse<Boolean>> accessCheck(@PathVariable UUID projectId,
                                                            @RequestParam UUID userId,
                                                            @RequestParam ProjectRoleEnum requiredRole) {
        return ResponseEntity.ok(ApiResponse.ok(projectMemberService.hasAccess(userId, projectId, requiredRole)));
    }
}
