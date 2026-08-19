package com.vplmqa.e2e.controllers;

import com.vplmqa.e2e.dto.CreateStepRequest;
import com.vplmqa.e2e.dto.StepResponse;
import com.vplmqa.e2e.dto.UpdateStepRequest;
import com.vplmqa.e2e.services.StepService;
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
@Tag(name = "Steps", description = "E2E Step Management API")
public class StepController {

    private final StepService stepService;

    public StepController(StepService stepService) {
        this.stepService = stepService;
    }

    @PostMapping("/scenarios/{scenarioId}/steps")
    @Operation(summary = "Create a new step for a scenario")
    public ResponseEntity<StepResponse> createStep(@PathVariable UUID scenarioId, @Valid @RequestBody CreateStepRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stepService.createStep(scenarioId, request));
    }

    @GetMapping("/scenarios/{scenarioId}/steps")
    @Operation(summary = "List steps for a scenario")
    public ResponseEntity<List<StepResponse>> listSteps(@PathVariable UUID scenarioId) {
        return ResponseEntity.ok(stepService.listStepsForScenario(scenarioId));
    }

    @GetMapping("/steps/{id}")
    @Operation(summary = "Get step details")
    public ResponseEntity<StepResponse> getStep(@PathVariable UUID id) {
        return ResponseEntity.ok(stepService.getStepWithDetails(id));
    }

    @PutMapping("/steps/{id}")
    @Operation(summary = "Update step")
    public ResponseEntity<StepResponse> updateStep(@PathVariable UUID id, @Valid @RequestBody UpdateStepRequest request) {
        return ResponseEntity.ok(stepService.updateStep(id, request));
    }

    @DeleteMapping("/steps/{id}")
    @Operation(summary = "Delete step")
    public ResponseEntity<Void> deleteStep(@PathVariable UUID id) {
        stepService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }
}
