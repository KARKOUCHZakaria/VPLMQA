package com.vplmqa.project.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.project.dto.ExtractionArtifactResponse;
import com.vplmqa.project.dto.StoreFigmaPageExtractionRequest;
import com.vplmqa.project.service.ProjectExtractionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/extraction")
public class ProjectExtractionController {

    private final ProjectExtractionService extractionService;

    public ProjectExtractionController(ProjectExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping("/figma/design")
    public ResponseEntity<ApiResponse<ExtractionArtifactResponse>> storeFigmaDesign(
            @PathVariable UUID projectId,
            @Valid @RequestBody StoreFigmaPageExtractionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(extractionService.storeFigmaDesign(projectId, request.pageJson())));
    }

    @PostMapping("/figma/design/extract")
    public ResponseEntity<ApiResponse<ExtractionArtifactResponse>> extractFigmaDesign(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(extractionService.extractFigmaDesign(projectId)));
    }

    @PostMapping("/figma/pages/{pageId}")
    public ResponseEntity<ApiResponse<ExtractionArtifactResponse>> storeFigmaPage(
            @PathVariable UUID projectId,
            @PathVariable UUID pageId,
            @Valid @RequestBody StoreFigmaPageExtractionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(extractionService.storeFigmaPage(projectId, pageId, request.pageJson())));
    }

    @PostMapping("/figma/pages/{pageId}/components")
    public ResponseEntity<ApiResponse<ExtractionArtifactResponse>> extractFigmaComponents(
            @PathVariable UUID projectId,
            @PathVariable UUID pageId) {
        return ResponseEntity.ok(ApiResponse.ok(extractionService.extractFigmaComponents(projectId, pageId)));
    }

    @PostMapping("/web/pages/{pageId}")
    public ResponseEntity<ApiResponse<ExtractionArtifactResponse>> extractWebPage(
            @PathVariable UUID projectId,
            @PathVariable UUID pageId) {
        return ResponseEntity.ok(ApiResponse.ok(extractionService.extractWebPage(projectId, pageId)));
    }

}
