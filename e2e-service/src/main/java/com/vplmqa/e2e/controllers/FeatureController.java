package com.vplmqa.e2e.controllers;

import com.vplmqa.e2e.dto.CreateFeatureRequest;
import com.vplmqa.e2e.dto.FeatureResponse;
import com.vplmqa.e2e.dto.FeatureWithHierarchyResponse;
import com.vplmqa.e2e.dto.UpdateFeatureRequest;
import com.vplmqa.e2e.services.FeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/features")
@Tag(name = "Features", description = "E2E Feature Management API")
public class FeatureController {
    private final FeatureService featureService;

    public FeatureController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @PostMapping
    @Operation(summary = "Create a new feature")
    public ResponseEntity<FeatureResponse> createFeature(@Valid @RequestBody CreateFeatureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(featureService.createFeature(request));
    }

    @GetMapping
    @Operation(summary = "List features")
    public ResponseEntity<Page<FeatureResponse>> listFeatures(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(featureService.listFeatures(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get feature by ID")
    public ResponseEntity<FeatureResponse> getFeature(@PathVariable UUID id) {
        return ResponseEntity.ok(featureService.getFeature(id));
    }

    @GetMapping("/{id}/full")
    @Operation(summary = "Get feature with all scenarios and steps")
    public ResponseEntity<FeatureWithHierarchyResponse> getFeatureFull(@PathVariable UUID id) {
        return ResponseEntity.ok(featureService.getFeatureWithHierarchy(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update feature")
    public ResponseEntity<FeatureResponse> updateFeature(@PathVariable UUID id, @Valid @RequestBody UpdateFeatureRequest request) {
        return ResponseEntity.ok(featureService.updateFeature(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete feature")
    public ResponseEntity<Void> deleteFeature(@PathVariable UUID id) {
        featureService.deleteFeature(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export-gherkin")
    @Operation(summary = "Generate and export Gherkin for feature")
    public ResponseEntity<String> exportGherkin(@PathVariable UUID id) {
        String gherkin = featureService.generateGherkinFromFeature(id);
        return ResponseEntity.ok().header("Content-Type", "text/plain").body(gherkin);
    }

    @PostMapping("/{id}/run-agent")
    @Operation(summary = "Generate Gherkin and start the LangGraph E2E agent pipeline")
    public ResponseEntity<Map<String, Object>> runAgentPipeline(@PathVariable UUID id) {
        return ResponseEntity.ok(featureService.runAgentPipeline(id));
    }

    @PostMapping("/import-gherkin")
    @Operation(summary = "Import Gherkin as a new feature")
    public ResponseEntity<FeatureResponse> importGherkin(@RequestBody String gherkin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(featureService.importGherkinAsFeature(gherkin));
    }
}
