package com.vplmqa.project.dto;

import java.util.List;
import java.util.UUID;

public record ExtractionArtifactResponse(
        UUID projectId,
        UUID pageId,
        String pageName,
        String source,
        String pageObjectPath,
        int componentCount,
        List<String> componentObjectPaths
) {
}
