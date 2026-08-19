package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.StepStatus;
import com.vplmqa.e2e.entities.StepType;
import java.time.LocalDateTime;
import java.util.UUID;

public record StepResponse(
    UUID id,
    UUID scenarioId,
    StepType type,
    String text,
    StepStatus status,
    Integer sequenceOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
