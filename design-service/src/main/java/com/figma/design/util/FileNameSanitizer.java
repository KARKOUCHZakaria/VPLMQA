package com.figma.design.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for sanitizing file names for storage in MinIO and file systems
 * Ensures file names contain only alphanumeric characters, hyphens, and underscores
 */
@Slf4j
@UtilityClass
public class FileNameSanitizer {

    /**
     * Sanitize a single name component (e.g., page name or project name)
     * Converts to lowercase, removes special characters, collapses underscores
     *
     * @param name The name to sanitize
     * @return Safe name containing only alphanumeric, hyphens, and underscores
     */
    public String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "unnamed";
        }

        // Step 1: Convert to lowercase
        String sanitized = name.toLowerCase().trim();

        // Step 2: Replace spaces and common separators with underscores
        sanitized = sanitized.replaceAll("[\\s\\-]+", "_");

        // Step 3: Remove all special characters, keep only alphanumeric, underscore, hyphen
        // This regex keeps: a-z, A-Z, 0-9, underscore, hyphen
        sanitized = sanitized.replaceAll("[^a-z0-9_\\-]", "");

        // Step 4: Collapse multiple consecutive underscores into single underscore
        sanitized = sanitized.replaceAll("_+", "_");

        // Step 5: Remove leading and trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        // Step 6: If result is empty, use default name
        if (sanitized.isEmpty()) {
            sanitized = "unnamed";
            log.warn("File name sanitization resulted in empty string, using default: {}", sanitized);
        }

        // Step 7: Limit length to prevent filesystem issues (max 200 chars)
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
            log.warn("File name truncated to 200 characters: {}", sanitized);
        }

        log.debug("Sanitized file name: '{}' -> '{}'", name, sanitized);
        return sanitized;
    }

    /**
     * Generate a safe object name for MinIO storage
     * Combines multiple name components with underscore separator
     *
     * @param components Name components (project name, page name, etc.)
     * @return Safe object name for MinIO
     */
    public String generateObjectName(String... components) {
        if (components == null || components.length == 0) {
            return "unnamed";
        }

        StringBuilder objectName = new StringBuilder();
        for (int i = 0; i < components.length; i++) {
            String sanitized = sanitize(components[i]);
            if (!sanitized.isEmpty()) {
                if (i > 0) {
                    objectName.append("_");
                }
                objectName.append(sanitized);
            }
        }

        String result = objectName.toString();
        if (result.isEmpty()) {
            result = "unnamed";
        }

        return result;
    }

    /**
     * Generate a safe file name with optional prefix path
     * Example: generateFileName("designs", "myproject", "homepage") -> "designs/myproject_homepage"
     *
     * @param pathPrefix Optional path prefix (e.g., "designs", "pages")
     * @param components Name components to combine
     * @return Safe file path for MinIO
     */
    public String generateFileName(String pathPrefix, String... components) {
        String objectName = generateObjectName(components);
        if (pathPrefix != null && !pathPrefix.trim().isEmpty()) {
            return pathPrefix + "/" + objectName;
        }
        return objectName;
    }

    /**
     * Generate safe file name with extension
     *
     * @param pathPrefix Optional path prefix
     * @param extension File extension (e.g., "json", "html")
     * @param components Name components
     * @return Safe file path with extension for MinIO
     */
    public String generateFileNameWithExtension(String pathPrefix, String extension, String... components) {
        String fileName = generateFileName(pathPrefix, components);
        if (extension != null && !extension.trim().isEmpty()) {
            String ext = extension.trim();
            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            return fileName + ext;
        }
        return fileName;
    }
}
