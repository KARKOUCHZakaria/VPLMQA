package com.vplmqa.e2e.controllers;

import com.vplmqa.e2e.dto.CreateTestExecutionRequest;
import com.vplmqa.e2e.dto.TestExecutionResponse;
import com.vplmqa.e2e.dto.UpdateTestExecutionRequest;
import com.vplmqa.e2e.services.TestExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
@Tag(name = "Test Executions", description = "E2E Test Execution Management API")
public class TestExecutionController {

    private final TestExecutionService testExecutionService;

    public TestExecutionController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @PostMapping
    @Operation(summary = "Create test execution record")
    public ResponseEntity<TestExecutionResponse> createTestExecution(@Valid @RequestBody CreateTestExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testExecutionService.recordTestExecution(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get test execution")
    public ResponseEntity<TestExecutionResponse> getExecution(@PathVariable UUID id) {
        return ResponseEntity.ok(testExecutionService.getExecution(id));
    }

    @GetMapping
    @Operation(summary = "List test executions (optionally by featureId)")
    public ResponseEntity<List<TestExecutionResponse>> listExecutions(@RequestParam(required = false) UUID featureId) {
        if (featureId != null) {
            return ResponseEntity.ok(testExecutionService.getExecutionHistory(featureId, 100));
        }
        return ResponseEntity.ok(testExecutionService.findAll());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update test execution result")
    public ResponseEntity<TestExecutionResponse> updateExecution(@PathVariable UUID id, @RequestBody UpdateTestExecutionRequest request) {
        return ResponseEntity.ok(testExecutionService.updateExecutionResults(id, request));
    }
}
