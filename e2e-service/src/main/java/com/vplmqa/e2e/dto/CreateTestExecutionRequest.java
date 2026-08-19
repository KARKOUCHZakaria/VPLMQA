package com.vplmqa.e2e.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTestExecutionRequest(
    @NotNull(message = "Feature ID is required")
    UUID featureId,
    
    UUID scenarioId,
    String status
) {}
