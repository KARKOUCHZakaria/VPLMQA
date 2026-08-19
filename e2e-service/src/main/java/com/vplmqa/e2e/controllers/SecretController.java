package com.vplmqa.e2e.controllers;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.e2e.dto.SecretValueRequest;
import com.vplmqa.e2e.services.VaultSecretService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/e2e/projects/{projectId}/secrets")
public class SecretController {
    private final VaultSecretService secretService;

    public SecretController(VaultSecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("aliases", secretService.listProjectSecretAliases(projectId))));
    }

    @PutMapping("/{alias}")
    public ResponseEntity<ApiResponse<Map<String, String>>> put(
            @PathVariable UUID projectId,
            @PathVariable String alias,
            @Valid @RequestBody SecretValueRequest request) {
        String reference = secretService.putProjectSecret(projectId, alias, request.value());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("alias", alias, "reference", reference)));
    }
}
