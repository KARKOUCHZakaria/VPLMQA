package com.vplmqa.project.service;

import org.springframework.stereotype.Service;

@Service
public class ExtractionPathService {
    private static final int MAX_PATH_PART_LENGTH = 80;

    public String figmaPagePath(String projectName, String pageName) {
        return project(projectName) + "/figma-pages/" + page(pageName) + "/page.json";
    }

    public String figmaPageImagePath(String projectName, String pageName) {
        return project(projectName) + "/figma-pages/" + page(pageName) + "/page.png";
    }

    public String figmaComponentPath(String projectName, String pageName, String componentName, int occurrence) {
        String component = sanitize(componentName);
        return project(projectName) + "/figma-pages/" + page(pageName) + "/components/" + component + "/" + component + "-" + occurrence + ".json";
    }

    public String webPagePath(String projectName, String pageName) {
        return project(projectName) + "/web-pages/" + page(pageName) + "/page.json";
    }

    public String webPageImagePath(String projectName, String pageName) {
        return project(projectName) + "/web-pages/" + page(pageName) + "/page.png";
    }

    public String designPath(String projectName) {
        return project(projectName) + "/design.json";
    }

    public String webComponentPath(String projectName, String pageName, String componentName, int occurrence) {
        String component = sanitize(componentName);
        return project(projectName) + "/web-pages/" + page(pageName) + "/components/" + component + "/" + component + "-" + occurrence + ".json";
    }

    public String mlDatasetJsonPath(String projectName, String pageName) {
        return project(projectName) + "/web-pages/" + page(pageName) + "/ml/design-token-comparison.json";
    }

    public String mlDatasetCsvPath(String projectName, String pageName) {
        return project(projectName) + "/web-pages/" + page(pageName) + "/ml/design-token-comparison.csv";
    }

    public String projectMlDatasetJsonPath(String projectName) {
        return project(projectName) + "/ml/design-token-comparison.json";
    }

    public String projectMlDatasetCsvPath(String projectName) {
        return project(projectName) + "/ml/design-token-comparison.csv";
    }

    private String project(String value) {
        return sanitize(value);
    }

    private String page(String value) {
        return sanitize(value);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unnamed";
        }
        // Only allow a-z, 0-9 and hyphen. Replace everything else (including dots and underscores) with hyphen.
        String sanitized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (sanitized.isBlank()) {
            return "unnamed";
        }
        if (sanitized.length() <= MAX_PATH_PART_LENGTH) {
            return sanitized;
        }
        String hash = Integer.toHexString(value.hashCode());
        int prefixLength = MAX_PATH_PART_LENGTH - hash.length() - 1;
        return sanitized.substring(0, Math.max(1, prefixLength)).replaceAll("-+$", "") + "-" + hash;
    }
}
