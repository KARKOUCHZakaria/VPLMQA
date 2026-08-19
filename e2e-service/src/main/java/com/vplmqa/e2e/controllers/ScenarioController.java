package com.vplmqa.e2e.controllers;

import com.vplmqa.e2e.dto.CreateScenarioRequest;
import com.vplmqa.e2e.dto.ScenarioResponse;
import com.vplmqa.e2e.dto.ScenarioWithStepsResponse;
import com.vplmqa.e2e.dto.UpdateScenarioRequest;
import com.vplmqa.e2e.services.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Scenarios", description = "E2E Scenario Management API")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @PostMapping("/features/{featureId}/scenarios")
    @Operation(summary = "Create a new scenario for a feature")
    public ResponseEntity<ScenarioResponse> createScenario(@PathVariable UUID featureId, @Valid @RequestBody CreateScenarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scenarioService.createScenario(featureId, request));
    }

    @GetMapping("/features/{featureId}/scenarios")
    @Operation(summary = "List scenarios for a feature")
    public ResponseEntity<List<ScenarioResponse>> listScenarios(@PathVariable UUID featureId) {
        return ResponseEntity.ok(scenarioService.listScenariosForFeature(featureId));
    }

    @GetMapping("/scenarios/{id}")
    @Operation(summary = "Get scenario with steps")
    public ResponseEntity<ScenarioWithStepsResponse> getScenario(@PathVariable UUID id) {
        return ResponseEntity.ok(scenarioService.getScenarioWithSteps(id));
    }

    @PutMapping("/scenarios/{id}")
    @Operation(summary = "Update scenario")
    public ResponseEntity<ScenarioResponse> updateScenario(@PathVariable UUID id, @Valid @RequestBody UpdateScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.updateScenario(id, request));
    }

    @DeleteMapping("/scenarios/{id}")
    @Operation(summary = "Delete scenario")
    public ResponseEntity<Void> deleteScenario(@PathVariable UUID id) {
        scenarioService.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }
}
