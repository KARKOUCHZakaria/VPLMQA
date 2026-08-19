package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.ProjectMlDatasetResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectComparisonService {

    private static final String COMPARISON_VERSION = "4.6-page-visual-evidence";

    private final ProjectMlDatasetService datasetService;
    private final DesignTokenMlInferenceService inferenceService;
    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;
    private final PageEnrichmentService enrichmentService;

    public ProjectComparisonService(ProjectMlDatasetService datasetService,
                                    DesignTokenMlInferenceService inferenceService,
                                    MinIOService minIOService,
                                    ObjectMapper objectMapper,
                                    PageEnrichmentService enrichmentService) {
        this.datasetService = datasetService;
        this.inferenceService = inferenceService;
        this.minIOService = minIOService;
        this.objectMapper = objectMapper;
        this.enrichmentService = enrichmentService;
    }

    public Map<String, Object> compare(UUID projectId, UUID pageId, boolean refresh) {
        ProjectMlDatasetResponse dataset = datasetService.generate(projectId, pageId);
        if (dataset.rows().isEmpty()) {
            throw new IllegalArgumentException("No comparable mapped components were found for this page.");
        }
        String pageName = firstNonBlank(
                String.valueOf(dataset.rows().get(0).getOrDefault("web_page", "")),
                String.valueOf(dataset.rows().get(0).getOrDefault("figma_page", "")), "page");
        String projectRoot = dataset.jsonObjectPath().contains("/ml/")
                ? dataset.jsonObjectPath().substring(0, dataset.jsonObjectPath().indexOf("/ml/"))
                : dataset.jsonObjectPath().split("/")[0];
        String pagePath = sanitize(pageName);
        String resultPath = projectRoot + "/comparisons/" + pagePath + "/result.json";
        if (!refresh) {
            try {
                Map<String, Object> cached = objectMapper.readValue(
                        minIOService.downloadDesign(resultPath), Map.class);
                if (COMPARISON_VERSION.equals(String.valueOf(cached.get("comparisonVersion")))) {
                    return cached;
                }
            } catch (Exception ignored) {
                // No cached result yet; run the comparison below.
            }
        }
        Map<String, Object> enrichment = enrichmentService.enrich(
                projectId, pageId, "Prepare this page for design comparison and Playwright E2E automation."
        );
        List<Map<String, Object>> comparisonRows = applyEnrichedFacts(dataset.rows(), enrichment);
        String figmaImagePath = projectRoot + "/figma-pages/" + pagePath + "/page.png";
        String webImagePath = projectRoot + "/web-pages/" + pagePath + "/page.png";
        String figmaImage = image(figmaImagePath);
        String webImage = image(webImagePath);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("page_name", pageName);
        request.put("rows", comparisonRows);
        request.put("figma_image_base64", figmaImage);
        request.put("web_image_base64", webImage);
        JsonNode agentResult = inferenceService.runComparison(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("pageId", pageId);
        result.put("pageName", pageName);
        result.put("comparisonVersion", COMPARISON_VERSION);
        result.put("generatedAt", Instant.now().toString());
        result.put("figmaImagePath", figmaImagePath);
        result.put("webImagePath", webImagePath);
        result.put("figmaImage", figmaImage.isBlank() ? "" : "data:image/png;base64," + figmaImage);
        result.put("webImage", webImage.isBlank() ? "" : "data:image/png;base64," + webImage);
        result.put("rows", comparisonRows);
        result.put("predictions", objectMapper.convertValue(agentResult.path("predictions"), Object.class));
        result.put("explanation", objectMapper.convertValue(agentResult.path("explanation"), Object.class));
        result.put("resultObjectPath", resultPath);
        result.put("enrichmentArtifacts", enrichment.get("artifacts"));
        try {
            minIOService.uploadText(resultPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result), "application/json");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to store page comparison result: " + exception.getMessage(), exception);
        }
        return result;
    }

    private List<Map<String, Object>> applyEnrichedFacts(List<Map<String, Object>> rows,
                                                          Map<String, Object> enrichment) {
        JsonNode root = objectMapper.valueToTree(enrichment);
        Map<String, JsonNode> figmaById = componentsById(root.path("figmaPage").path("components"));
        Map<String, JsonNode> webById = componentsById(root.path("webPage").path("components"));
        List<Map<String, Object>> enrichedRows = new ArrayList<>();
        for (Map<String, Object> original : rows) {
            Map<String, Object> row = new LinkedHashMap<>(original);
            JsonNode figma = figmaById.get(String.valueOf(row.getOrDefault("figma_component_id", "")));
            JsonNode web = webById.get(String.valueOf(row.getOrDefault("web_component_id", "")));
            fillMissing(row, "figma_color", tokenValue(figma, "color", "backgroundColor"));
            fillMissing(row, "code_color", tokenValue(web, "color", "backgroundColor"));
            fillMissing(row, "figma_spacing", tokenValue(figma, "spacing", "padding", "gap"));
            fillMissing(row, "code_spacing", tokenValue(web, "spacing", "padding", "gap"));
            fillMissing(row, "figma_font_size", tokenValue(figma, "fontSize"));
            fillMissing(row, "code_font_size", tokenValue(web, "fontSize"));
            fillMissing(row, "figma_font_weight", tokenValue(figma, "fontWeight"));
            fillMissing(row, "code_font_weight", tokenValue(web, "fontWeight"));
            fillMissing(row, "figma_border_radius", tokenValue(figma, "borderRadius"));
            fillMissing(row, "code_border_radius", tokenValue(web, "borderRadius"));
            fillMissing(row, "figma_width", tokenValue(figma, "width"));
            fillMissing(row, "code_width", tokenValue(web, "width"));
            fillMissing(row, "figma_height", tokenValue(figma, "height"));
            fillMissing(row, "code_height", tokenValue(web, "height"));
            if (web != null) {
                String existingName = String.valueOf(row.getOrDefault("uniqueName", row.get("component")));
                String enrichedName = web.path("uniqueName").asText("");
                if (!enrichedName.isBlank() && isStableName(enrichedName)) {
                    row.put("uniqueName", enrichedName);
                    row.put("component", enrichedName);
                } else {
                    row.put("uniqueName", existingName);
                    row.put("component", existingName);
                }
            }
            normalizeRow(row);
            enrichedRows.add(row);
        }
        return enrichedRows;
    }

    private Map<String, JsonNode> componentsById(JsonNode components) {
        Map<String, JsonNode> result = new HashMap<>();
        if (components.isArray()) {
            for (JsonNode component : components) {
                String id = component.path("componentId").asText("");
                if (!id.isBlank()) result.put(id, component);
            }
        }
        return result;
    }

    private Object tokenValue(JsonNode component, String... fields) {
        if (component == null) return null;
        JsonNode tokens = component.path("facts").path("designTokens");
        for (String field : fields) {
            JsonNode value = tokens.path(field).path("value");
            if (!value.isMissingNode() && !value.isNull() && !value.asText("").isBlank()) {
                return value.isNumber() ? value.numberValue() : value.asText();
            }
        }
        return null;
    }

    private void fillMissing(Map<String, Object> row, String key, Object enrichedValue) {
        if (enrichedValue == null) return;
        Object current = row.get(key);
        boolean missing = current == null || String.valueOf(current).isBlank()
                || (current instanceof Number number && number.doubleValue() == 0.0);
        if (missing) row.put(key, enrichedValue);
    }

    private void normalizeRow(Map<String, Object> row) {
        row.put("figma_color", normalizeColor(row.get("figma_color")));
        row.put("code_color", normalizeColor(row.get("code_color")));
        List.of("figma_spacing", "code_spacing", "figma_font_size", "code_font_size",
                "figma_border_radius", "code_border_radius", "figma_width", "code_width",
                "figma_height", "code_height").forEach(key -> row.put(key, numberToken(row.get(key))));
        row.put("figma_font_weight", normalizeFontWeight(row.get("figma_font_weight")));
        row.put("code_font_weight", normalizeFontWeight(row.get("code_font_weight")));
    }

    private boolean isStableName(String value) {
        String text = value == null ? "" : value.trim();
        String lower = text.toLowerCase();
        return text.matches("[a-zA-Z0-9_.-]+")
                && text.length() <= 80
                && !lower.contains(".component_")
                && !lower.startsWith("component_");
    }

    private String normalizeColor(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return "";
        String text = String.valueOf(value).trim();
        if (text.startsWith("#")) {
            String hex = text.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }
            return hex.length() >= 6 ? "#" + hex.substring(0, 6).toUpperCase() : text.toUpperCase();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("rgba?\\(([^)]+)\\)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (matcher.find()) {
            String[] parts = matcher.group(1).split(",");
            if (parts.length >= 3) {
                return String.format("#%02X%02X%02X", colorChannel(parts[0]), colorChannel(parts[1]), colorChannel(parts[2]));
            }
        }
        return text;
    }

    private int colorChannel(String value) {
        try {
            String text = value == null ? "" : value.trim();
            double parsed = text.endsWith("%")
                    ? Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 2.55
                    : Double.parseDouble(text);
            return Math.max(0, Math.min(255, (int) Math.round(parsed)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int numberToken(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return (int) Math.round(number.doubleValue());
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?")
                .matcher(String.valueOf(value));
        return matcher.find() ? (int) Math.round(Double.parseDouble(matcher.group())) : 0;
    }

    private String normalizeFontWeight(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return "400";
        String text = String.valueOf(value).trim().toLowerCase();
        return switch (text) {
            case "normal", "regular" -> "400";
            case "medium" -> "500";
            case "semibold", "semi-bold", "demibold", "demi-bold" -> "600";
            case "bold" -> "700";
            default -> {
                int parsed = numberToken(text);
                yield parsed > 0 ? String.valueOf(parsed) : String.valueOf(value).trim();
            }
        };
    }

    private String image(String path) {
        try {
            return Base64.getEncoder().encodeToString(minIOService.downloadBytes(path));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String sanitize(String value) {
        String result = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return result.isBlank() ? "page" : result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
