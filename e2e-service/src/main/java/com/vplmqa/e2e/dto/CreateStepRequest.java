package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.StepType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStepRequest(
    @NotNull(message = "Type is required")
    StepType type,
    
    @NotBlank(message = "Text is required")
    @Size(max = 1000, message = "Text must be at most 1000 characters")
    String text,
    
    Integer sequenceOrder
) {}
