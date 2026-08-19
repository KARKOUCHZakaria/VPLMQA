package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.ExecutionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TestExecutionResponse(
    UUID id,
    UUID featureId,
    UUID scenarioId,
    ExecutionStatus status,
    Long executionTimeMs,
    String outputLog,
    String errorMessage,
    String screenshots,
    LocalDateTime createdAt
) {}
