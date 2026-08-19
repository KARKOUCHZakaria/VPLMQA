package com.vplmqa.e2e.controllers;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.e2e.services.StepFunctionRegistryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/e2e/step-functions")
public class StepFunctionRegistryController {
    private final StepFunctionRegistryService service;

    public StepFunctionRegistryController(StepFunctionRegistryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(status)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> status(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        service.setStatus(id, request.getOrDefault("status", ""));
        return ResponseEntity.ok(ApiResponse.ok(null, "Step function status updated"));
    }
}
