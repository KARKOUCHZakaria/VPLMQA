package com.vplmqa.e2e.dto;

import com.vplmqa.e2e.entities.StepType;
import jakarta.validation.constraints.Size;

public record UpdateStepRequest(
    StepType type,
    
    @Size(max = 1000, message = "Text must be at most 1000 characters")
    String text,
    
    String status,
    Integer sequenceOrder
) {}
