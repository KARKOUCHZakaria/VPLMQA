package com.figma.design.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DesignTokenPrediction(
        int index,
        String component,
        String modelComponent,
        boolean match,
        double matchProbability
) {
}
