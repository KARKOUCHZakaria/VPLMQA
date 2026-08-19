package com.figma.design.dto.figma;

public record ProjectDesignImportRequest(
        Long projectId,
        String fileKey,
        String fileUrl,
        String apiToken,
        Boolean persist) {

    public String resolveFileKey() {
        if (fileKey != null && !fileKey.isBlank()) {
            return fileKey.trim();
        }

        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        String normalizedUrl = fileUrl.trim().replaceFirst("\\?.*$", "");
        String extractedKey = normalizedUrl.replaceFirst("^.*?/(?:file|design)/", "");
        int slashIndex = extractedKey.indexOf('/');
        if (slashIndex >= 0) {
            extractedKey = extractedKey.substring(0, slashIndex);
        }
        return extractedKey.trim();
    }

    public boolean shouldPersist() {
        return Boolean.TRUE.equals(persist) || projectId != null;
    }
}
