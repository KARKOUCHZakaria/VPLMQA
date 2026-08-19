package com.figma.design.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectMlDatasetResponse(
        UUID projectId,
        int rowCount,
        String jsonObjectPath,
        String csvObjectPath,
        List<Map<String, Object>> rows
) {
}
