package com.vplmqa.member.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.member.enumtype.ProjectRoleEnum;
import com.vplmqa.member.service.ProjectMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for fast access checks used by the Gateway.
 */
@RestController
@RequestMapping("/api/v1/members/access-check")
public class AccessCheckController {

    private final ProjectMemberService projectMemberService;

    public AccessCheckController(ProjectMemberService projectMemberService) {
        this.projectMemberService = projectMemberService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> accessCheck(@RequestParam UUID userId,
                                                            @RequestParam UUID projectId,
                                                            @RequestParam ProjectRoleEnum requiredRole) {
        boolean hasAccess = projectMemberService.hasAccess(userId, projectId, requiredRole);
        return ResponseEntity.ok(Map.of("hasAccess", hasAccess));
    }
}
