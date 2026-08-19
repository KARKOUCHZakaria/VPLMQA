package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.FeatureStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record FeatureResponse(
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
    com.vplmqa.e2e.entities.TargetMode targetMode
) {}
