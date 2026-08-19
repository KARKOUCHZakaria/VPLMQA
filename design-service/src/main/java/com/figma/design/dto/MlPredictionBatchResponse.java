package com.figma.design.dto;

import java.util.List;

public record MlPredictionBatchResponse(
        String modelVersion,
        List<DesignTokenPrediction> predictions
) {
}
