package com.vplmqa.project.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MlDatasetResponse(
        UUID projectId,
        int rowCount,
        String jsonObjectPath,
        String csvObjectPath,
        List<Map<String, Object>> rows
) {
}
