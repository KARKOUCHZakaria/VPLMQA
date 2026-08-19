package com.figma.design.dto;

import java.util.List;

public record DesignTokenComparisonArtifactResponse(
        Long webPageId,
        String projectName,
        String pageName,
        int rowCount,
        String jsonMinioPath,
        String csvMinioPath,
        List<DesignTokenComparisonRow> rows
) {
}
