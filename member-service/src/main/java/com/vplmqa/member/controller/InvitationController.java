package com.vplmqa.member.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.member.dto.InvitationRequest;
import com.vplmqa.member.dto.InvitationResponse;
import com.vplmqa.member.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for invitations.
 */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(@PathVariable UUID orgId,
                                                                   @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(invitationService.invite(request.email(), orgId, request.orgRole(), request.createdBy())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> listPending(@PathVariable UUID orgId) {
        return ResponseEntity.ok(ApiResponse.ok(invitationService.listPending(orgId)));
    }

    @PostMapping("/accept")
    public ResponseEntity<ApiResponse<InvitationResponse>> accept(@RequestBody String token) {
        return ResponseEntity.ok(ApiResponse.ok(invitationService.acceptInvitation(token)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<InvitationResponse>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(invitationService.revokeInvitation(id)));
    }
}
