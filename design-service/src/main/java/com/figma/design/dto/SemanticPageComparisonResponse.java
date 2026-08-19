package com.figma.design.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record SemanticPageComparisonResponse(
        JsonNode figmaPageJson,
        JsonNode webPageJson,
        List<ComponentSemanticMapping> mappings,
        String model,
        boolean focusedOnE2eComponents
) {
    public record ComponentSemanticMapping(
            String uniqueName,
            String role,
            String usage,
            String figmaComponentId,
            String webComponentId,
            String webSelector,
            boolean usedInE2e,
            double confidence
    ) {
    }
}
