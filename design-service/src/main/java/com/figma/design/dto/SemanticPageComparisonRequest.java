package com.figma.design.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SemanticPageComparisonRequest(
        @NotNull JsonNode figmaPageJson,
        @NotNull JsonNode webPageJson,
        String gherkinFeature,
        List<String> e2eComponentHints,
        Boolean focusE2eComponentsOnly
) {
}
