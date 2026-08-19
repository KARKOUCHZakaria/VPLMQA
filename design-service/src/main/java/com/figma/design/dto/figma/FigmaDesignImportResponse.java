package com.figma.design.dto.figma;

import java.util.List;

public record FigmaDesignImportResponse(
        String fileKey,
        String fileName,
        List<ImportedPage> pages,
        int extractedComponents,
        int persistedComponents,
        String minioPath) {

    public record ImportedPage(
            String id,
            String name,
            List<ImportedComponent> components) {
    }

    public record ImportedComponent(
            String id,
            String name,
            String type,
            Double x,
            Double y,
            Double width,
            Double height,
            String text) {
    }
}