package com.vplmqa.e2e.dto;

import jakarta.validation.constraints.Size;
import com.vplmqa.e2e.entities.TargetMode;
import java.util.UUID;

public record UpdateFeatureRequest(
    @Size(max = 255, message = "Name must be at most 255 characters")
    String name,
    String description,
    String gherkinContent,
    String status,
    UUID projectId,
    UUID defaultPageId,
    TargetMode targetMode
) {}
