package com.vplmqa.member.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.member.dto.OrgMemberRequest;
import com.vplmqa.member.dto.OrgMemberResponse;
import com.vplmqa.member.dto.OrganizationRequest;
import com.vplmqa.member.dto.OrganizationResponse;
import com.vplmqa.member.enumtype.OrgRoleEnum;
import com.vplmqa.member.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for organizations.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationResponse>> create(@Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.get(id)));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<OrgMemberResponse>>> members(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.getMembers(id)));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<OrgMemberResponse>> addMember(@PathVariable UUID id,
                                                                    @RequestBody OrgMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.addMember(id, request)));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        organizationService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member removed"));
    }

    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<ApiResponse<OrgMemberResponse>> changeRole(@PathVariable UUID id,
                                                                     @PathVariable UUID userId,
                                                                     @RequestBody OrgRoleEnum role) {
        return ResponseEntity.ok(ApiResponse.ok(organizationService.changeMemberRole(id, userId, role)));
    }
}
