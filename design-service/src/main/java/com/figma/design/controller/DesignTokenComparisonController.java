package com.figma.design.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.figma.design.dto.DesignTokenComparisonArtifactResponse;
import com.figma.design.dto.DesignTokenComparisonRow;
import com.figma.design.dto.MlPredictionBatchResponse;
import com.figma.design.dto.ProjectMlPredictionResponse;
import com.figma.design.dto.ProjectMlDatasetResponse;
import com.figma.design.service.DesignTokenMlInferenceService;
import com.figma.design.service.DesignTokenComparisonExportService;
import com.figma.design.service.ProjectMlDatasetService;
import com.figma.design.service.ProjectComparisonService;
import com.figma.design.service.ComponentOrganizationService;
import com.figma.design.service.PageEnrichmentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/design-token-comparison")
@RequiredArgsConstructor
public class DesignTokenComparisonController {

    private final DesignTokenComparisonExportService exportService;
    private final ProjectMlDatasetService projectMlDatasetService;
    private final DesignTokenMlInferenceService mlInferenceService;
    private final ProjectComparisonService projectComparisonService;
    private final ComponentOrganizationService componentOrganizationService;
    private final PageEnrichmentService pageEnrichmentService;

    @GetMapping("/rows")
    public List<DesignTokenComparisonRow> rows(
            @RequestParam(required = false) Long webPageId,
            @RequestParam(defaultValue = "true") Boolean e2eOnly) {
        return exportService.exportRows(webPageId, e2eOnly);
    }

    @GetMapping(value = "/rows.csv", produces = "text/csv")
    public ResponseEntity<String> csv(
            @RequestParam(required = false) Long webPageId,
            @RequestParam(defaultValue = "true") Boolean e2eOnly) {
        List<DesignTokenComparisonRow> rows = exportService.exportRows(webPageId, e2eOnly);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=design-token-comparison.csv")
                .contentType(new MediaType("text", "csv"))
                .body(exportService.toCsv(rows));
    }

    @PostMapping("/store")
    public DesignTokenComparisonArtifactResponse store(
            @RequestParam Long webPageId,
            @RequestParam(defaultValue = "true") Boolean e2eOnly) {
        return exportService.exportAndStore(webPageId, e2eOnly);
    }

    @PostMapping("/projects/{projectId}/dataset")
    public ProjectMlDatasetResponse generateProjectDataset(@org.springframework.web.bind.annotation.PathVariable UUID projectId,
                                                           @RequestParam(required = false) UUID pageId) {
        return projectMlDatasetService.generate(projectId, pageId);
    }

    @GetMapping("/model/status")
    public Map<String, Object> modelStatus() {
        return mlInferenceService.status();
    }

    @PostMapping("/predict")
    public MlPredictionBatchResponse predict(@org.springframework.web.bind.annotation.RequestBody List<Map<String, Object>> rows) {
        return mlInferenceService.predict(rows);
    }

    @PostMapping("/projects/{projectId}/predict")
    public ProjectMlPredictionResponse predictProjectDataset(
            @org.springframework.web.bind.annotation.PathVariable UUID projectId,
            @RequestParam(required = false) UUID pageId) {
        ProjectMlDatasetResponse dataset = projectMlDatasetService.generate(projectId, pageId);
        MlPredictionBatchResponse prediction = mlInferenceService.predict(dataset.rows());
        return new ProjectMlPredictionResponse(
                dataset.projectId(), dataset.rowCount(), dataset.jsonObjectPath(), dataset.csvObjectPath(),
                prediction.modelVersion(), dataset.rows(), prediction.predictions());
    }

    @PostMapping("/projects/{projectId}/compare")
    public Map<String, Object> compareProjectPage(
            @org.springframework.web.bind.annotation.PathVariable UUID projectId,
            @RequestParam UUID pageId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return projectComparisonService.compare(projectId, pageId, refresh);
    }

    @PostMapping("/projects/{projectId}/organize-components")
    public Map<String, Object> organizeProjectPageComponents(
            @org.springframework.web.bind.annotation.PathVariable UUID projectId,
            @RequestParam UUID pageId,
            @RequestParam(defaultValue = "") String workflowContext) {
        return componentOrganizationService.organize(projectId, pageId, workflowContext);
    }

    @PostMapping("/projects/{projectId}/enrich")
    public Map<String, Object> enrichProjectPage(
            @org.springframework.web.bind.annotation.PathVariable UUID projectId,
            @RequestParam UUID pageId,
            @RequestParam(defaultValue = "") String workflowContext) {
        return pageEnrichmentService.enrich(projectId, pageId, workflowContext);
    }

    @GetMapping("/projects/{projectId}/enrichment/catalog")
    public JsonNode enrichedE2eCatalog(
            @org.springframework.web.bind.annotation.PathVariable UUID projectId,
            @RequestParam UUID pageId) {
        return pageEnrichmentService.catalog(projectId, pageId);
    }
}
