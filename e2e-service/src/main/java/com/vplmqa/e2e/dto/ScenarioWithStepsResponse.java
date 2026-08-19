package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.ScenarioStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ScenarioWithStepsResponse(
    UUID id,
    UUID featureId,
    String name,
    String description,
    ScenarioStatus status,
    Integer sequenceOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<StepResponse> steps
) {}
