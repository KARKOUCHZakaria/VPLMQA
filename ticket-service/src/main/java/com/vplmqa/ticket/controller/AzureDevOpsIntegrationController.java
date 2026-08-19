package com.vplmqa.ticket.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.ticket.dto.AzureDevOpsConnectionRequest;
import com.vplmqa.ticket.dto.AzureDevOpsConnectionResponse;
import com.vplmqa.ticket.dto.AzureDevOpsMember;
import com.vplmqa.ticket.service.AzureDevOpsService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/integrations/azure-devops/projects/{projectId}")
public class AzureDevOpsIntegrationController {
    private final AzureDevOpsService service;

    public AzureDevOpsIntegrationController(AzureDevOpsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AzureDevOpsConnectionResponse>> get(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(projectId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AzureDevOpsConnectionResponse>> save(
            @PathVariable UUID projectId,
            @RequestBody AzureDevOpsConnectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.save(projectId, request), "Azure DevOps settings saved."));
    }

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<AzureDevOpsMember>>> members(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(service.members(projectId)));
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(@PathVariable UUID projectId) {
        List<AzureDevOpsMember> members = service.members(projectId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "connected", true,
                "memberCount", members.size()
        ), "Azure DevOps connection successful."));
    }
}
