package com.vplmqa.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record StoreFigmaPageExtractionRequest(@NotNull JsonNode pageJson) {
}
