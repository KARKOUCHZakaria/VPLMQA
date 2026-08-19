package com.figma.design.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectMlPredictionResponse(
        UUID projectId,
        int rowCount,
        String jsonObjectPath,
        String csvObjectPath,
        String modelVersion,
        List<Map<String, Object>> rows,
        List<DesignTokenPrediction> predictions
) {
}
