package com.vplmqa.e2e.dto;

import jakarta.validation.constraints.Size;

public record UpdateScenarioRequest(
    @Size(max = 255, message = "Name must be at most 255 characters")
    String name,
    String description,
    String status,
    Integer sequenceOrder
) {}
