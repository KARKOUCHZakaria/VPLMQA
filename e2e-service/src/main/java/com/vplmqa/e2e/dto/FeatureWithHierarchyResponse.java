package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.FeatureStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FeatureWithHierarchyResponse(
    UUID id,
    String name,
    String description,
    String gherkinContent,
    FeatureStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy,
    UUID projectId,
    UUID defaultPageId,
    com.vplmqa.e2e.entities.TargetMode targetMode,
    List<ScenarioWithStepsResponse> scenarios
) {}
