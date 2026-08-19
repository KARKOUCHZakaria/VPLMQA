package com.vplmqa.project.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.project.dto.ComponentRequest;
import com.vplmqa.project.dto.ComponentResponse;
import com.vplmqa.project.dto.ComponentSemanticUpdateRequest;
import com.vplmqa.project.service.ComponentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for project component operations.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/components")
public class ComponentController {

    private final ComponentService componentService;

    /**
     * Creates the controller.
     *
     * @param componentService component service
     */
    public ComponentController(ComponentService componentService) {
        this.componentService = componentService;
    }

    /**
     * Lists components for a project.
     *
     * @param projectId project id
     * @return component list
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(componentService.findByProject(projectId)));
    }

    /**
     * Lists components for a page.
     *
     * @param projectId project id
     * @param pageId page id
     * @return component list
     */
    @GetMapping("/page/{pageId}")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> listByPage(@PathVariable UUID projectId,
                                                                           @PathVariable UUID pageId) {
        return ResponseEntity.ok(ApiResponse.ok(componentService.findByPage(pageId)));
    }

    /**
     * Bulk upserts components.
     *
     * @param projectId project id
     * @param requests component requests
     * @return upserted components
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> bulkUpsert(@PathVariable UUID projectId,
                                                                           @Valid @RequestBody List<ComponentRequest> requests) {
        return ResponseEntity.ok(ApiResponse.ok(componentService.bulkUpsert(requests)));
    }

    @PostMapping("/semantic/bulk")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> applySemanticUpdates(
            @PathVariable UUID projectId,
            @Valid @RequestBody List<ComponentSemanticUpdateRequest> requests) {
        return ResponseEntity.ok(ApiResponse.ok(componentService.applySemanticUpdates(projectId, requests)));
    }

    /**
     * Finds a component by canonical name.
     *
     * @param projectId project id
     * @param canonicalName canonical name
     * @return component response
     */
    @GetMapping("/{canonicalName}")
    public ResponseEntity<ApiResponse<ComponentResponse>> getByCanonicalName(@PathVariable UUID projectId,
                                                                             @PathVariable String canonicalName) {
        return ResponseEntity.ok(ApiResponse.ok(componentService.findByCanonicalName(canonicalName, projectId)));
    }
}
