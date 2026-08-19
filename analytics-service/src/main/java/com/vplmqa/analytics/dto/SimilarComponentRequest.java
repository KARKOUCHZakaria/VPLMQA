package com.vplmqa.analytics.dto;

import java.util.UUID;

public record SimilarComponentRequest(float[] embedding, UUID projectId, int topK) {
}
