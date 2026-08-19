package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.ScenarioStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScenarioResponse(
    UUID id,
    UUID featureId,
    String name,
    String description,
    ScenarioStatus status,
    Integer sequenceOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
