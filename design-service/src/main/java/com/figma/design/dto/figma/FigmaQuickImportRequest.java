package com.figma.design.dto.figma;

public record FigmaQuickImportRequest(
        String fileKey,
        String apiToken) {
}
