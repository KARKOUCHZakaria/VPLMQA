package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.DesignTokenComparisonArtifactResponse;
import com.figma.design.dto.DesignTokenComparisonRow;
import com.figma.design.model.FigmaComponent;
import com.figma.design.model.Page;
import com.figma.design.model.WebComponent;
import com.figma.design.repository.FigmaComponentRepository;
import com.figma.design.repository.PageRepository;
import com.figma.design.repository.WebComponentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DesignTokenComparisonExportService {

    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final WebComponentRepository webComponentRepository;
    private final FigmaComponentRepository figmaComponentRepository;
    private final PageRepository pageRepository;
    private final MinIOService minIOService;
    private final DesignStoragePathService pathService;
    private final ObjectMapper objectMapper;

    public List<DesignTokenComparisonRow> exportRows(Long webPageId, Boolean e2eOnly) {
        List<WebComponent> webComponents = webPageId == null
                ? webComponentRepository.findAll()
                : webComponentRepository.findByPage_Id(webPageId);

        List<DesignTokenComparisonRow> rows = new ArrayList<>();
        for (WebComponent webComponent : webComponents) {
            if (Boolean.TRUE.equals(e2eOnly) && isBlank(webComponent.getTestIdentifier())) {
                continue;
            }
            if (webComponent.getMappedFigmaComponentId() == null) {
                continue;
            }

            Optional<FigmaComponent> figmaComponent = figmaComponentRepository.findById(webComponent.getMappedFigmaComponentId());
            if (figmaComponent.isEmpty()) {
                continue;
            }

            rows.add(toRow(webComponent, figmaComponent.get()));
        }
        return rows;
    }

    public DesignTokenComparisonArtifactResponse exportAndStore(Long webPageId, Boolean e2eOnly) {
        Page page = pageRepository.findById(webPageId)
                .orElseThrow(() -> new RuntimeException("Page not found: " + webPageId));
        List<DesignTokenComparisonRow> rows = exportRows(webPageId, e2eOnly);

        String projectName = page.getProject().getName();
        String pageName = page.getName();
        String jsonPath = pathService.designTokenComparisonJsonPath(projectName, pageName);
        String csvPath = pathService.designTokenComparisonCsvPath(projectName, pageName);

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
            minIOService.uploadText(jsonPath, json, "application/json");
            minIOService.uploadText(csvPath, toCsv(rows), "text/csv");
        } catch (Exception exception) {
            throw new RuntimeException("Failed to store design token comparison artifacts: " + exception.getMessage(), exception);
        }

        return new DesignTokenComparisonArtifactResponse(
                webPageId,
                projectName,
                pageName,
                rows.size(),
                jsonPath,
                csvPath,
                rows
        );
    }

    public String toCsv(List<DesignTokenComparisonRow> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("component,figma_color,code_color,figma_spacing,code_spacing,figma_font_size,code_font_size,figma_font_weight,code_font_weight,figma_border_radius,code_border_radius,figma_width,code_width,figma_height,code_height\n");
        for (DesignTokenComparisonRow row : rows) {
            csv.append(escape(row.component())).append(',')
                    .append(escape(row.figmaColor())).append(',')
                    .append(escape(row.codeColor())).append(',')
                    .append(row.figmaSpacing()).append(',')
                    .append(row.codeSpacing()).append(',')
                    .append(row.figmaFontSize()).append(',')
                    .append(row.codeFontSize()).append(',')
                    .append(escape(row.figmaFontWeight())).append(',')
                    .append(escape(row.codeFontWeight())).append(',')
                    .append(row.figmaBorderRadius()).append(',')
                    .append(row.codeBorderRadius()).append(',')
                    .append(row.figmaWidth()).append(',')
                    .append(row.codeWidth()).append(',')
                    .append(row.figmaHeight()).append(',')
                    .append(row.codeHeight()).append('\n');
        }
        return csv.toString();
    }

    private DesignTokenComparisonRow toRow(WebComponent webComponent, FigmaComponent figmaComponent) {
        JsonNode figmaJson = parseJson(figmaComponent.getRawJson());
        JsonNode webJson = parseJson(webComponent.getRawJson());
        String componentName = firstNonBlank(
                webComponent.getTestIdentifier(),
                webComponent.getFunctionalRole(),
                webComponent.getComponentName(),
                figmaComponent.getFigmaNodeName()
        );

        return new DesignTokenComparisonRow(
                componentName,
                figmaColor(figmaJson),
                webColor(webJson, webComponent),
                figmaSpacing(figmaJson),
                webSpacing(webJson),
                figmaFontSize(figmaJson),
                webPixelValue(webJson, "font-size", "fontSize"),
                figmaFontWeight(figmaJson),
                webFontWeight(webJson),
                figmaRadius(figmaJson),
                webPixelValue(webJson, "border-radius", "borderRadius"),
                rounded(firstNumber(figmaComponent.getNodeWidth(), pathDouble(figmaJson, "absoluteBoundingBox", "width"))),
                rounded(firstNumber(webComponent.getWidth(), pathDouble(webJson, "size", "width"), pathDouble(webJson, "boundingBox", "width"))),
                rounded(firstNumber(figmaComponent.getNodeHeight(), pathDouble(figmaJson, "absoluteBoundingBox", "height"))),
                rounded(firstNumber(webComponent.getHeight(), pathDouble(webJson, "size", "height"), pathDouble(webJson, "boundingBox", "height")))
        );
    }

    private JsonNode parseJson(String json) {
        if (isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String figmaColor(JsonNode node) {
        JsonNode fills = node.path("fills");
        if (fills.isArray()) {
            for (JsonNode fill : fills) {
                if ("SOLID".equalsIgnoreCase(fill.path("type").asText()) && fill.path("visible").asBoolean(true)) {
                    return rgbaToHex(fill.path("color"));
                }
            }
        }
        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                String color = figmaColor(child);
                if (!isBlank(color)) {
                    return color;
                }
            }
        }
        return "";
    }

    private String webColor(JsonNode node, WebComponent webComponent) {
        String tag = webComponent.getHtmlTag() == null ? "" : webComponent.getHtmlTag().toLowerCase(Locale.ROOT);
        if ("button".equals(tag) || "select".equals(tag) || "a".equals(tag)) {
            String background = styleText(node, "background-color", "backgroundColor");
            if (hasVisibleColor(background)) {
                return background;
            }
        }
        String color = styleText(node, "color");
        if (hasVisibleColor(color)) {
            return color;
        }
        String background = styleText(node, "background-color", "backgroundColor");
        return hasVisibleColor(background) ? background : "";
    }

    private int figmaSpacing(JsonNode node) {
        int direct = firstInt(
                intValue(node, -1, "itemSpacing"),
                intValue(node, -1, "paddingLeft"),
                intValue(node, -1, "paddingTop"),
                intValue(node, -1, "paddingRight"),
                intValue(node, -1, "paddingBottom"),
                intValue(node, -1, "layout", "spacing")
        );
        return direct > 0 ? direct : maxChildInt(node, this::figmaSpacing);
    }

    private int webSpacing(JsonNode node) {
        int margin = webPixelValue(node, "margin", "marginTop");
        if (margin > 0) {
            return margin;
        }
        return webPixelValue(node, "padding", "paddingTop");
    }

    private String figmaFontWeight(JsonNode node) {
        String value = pathText(node, "style", "fontWeight");
        if (!isBlank(value)) {
            return value;
        }
        JsonNode child = firstChildWithTextStyle(node);
        return child == null ? "" : pathText(child, "style", "fontWeight");
    }

    private String webFontWeight(JsonNode node) {
        return styleText(node, "font-weight", "fontWeight");
    }

    private int figmaRadius(JsonNode node) {
        int direct = firstInt(
                intValue(node, -1, "cornerRadius"),
                intValue(node, -1, "rectangleCornerRadii", "0")
        );
        return direct > 0 ? direct : maxChildInt(node, this::figmaRadius);
    }

    private int figmaFontSize(JsonNode node) {
        int direct = intValue(node, -1, "style", "fontSize");
        if (direct >= 0) {
            return direct;
        }
        JsonNode child = firstChildWithTextStyle(node);
        return child == null ? 0 : intValue(child, 0, "style", "fontSize");
    }

    private JsonNode firstChildWithTextStyle(JsonNode node) {
        JsonNode children = node.path("children");
        if (!children.isArray()) {
            return null;
        }
        for (JsonNode child : children) {
            if (child.path("style").isObject()
                    && (child.path("style").has("fontSize") || child.path("style").has("fontWeight"))) {
                return child;
            }
            JsonNode nested = firstChildWithTextStyle(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private int maxChildInt(JsonNode node, TokenExtractor extractor) {
        int max = 0;
        JsonNode children = node.path("children");
        if (!children.isArray()) {
            return 0;
        }
        for (JsonNode child : children) {
            max = Math.max(max, extractor.extract(child));
        }
        return max;
    }

    private String styleText(JsonNode node, String... names) {
        JsonNode computed = firstExisting(node.path("computedStyle"), node.path("styles"), node.path("style"));
        for (String name : names) {
            String value = computed.path(name).asText("");
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private int webPixelValue(JsonNode node, String... names) {
        for (String name : names) {
            int parsed = parsePixel(styleText(node, name));
            if (parsed >= 0) {
                return parsed;
            }
        }
        return 0;
    }

    private int intValue(JsonNode node, int defaultValue, String... path) {
        JsonNode current = node;
        for (String item : path) {
            current = current.path(item);
        }
        if (current.isNumber()) {
            return current.asInt();
        }
        return parsePixel(current.asText(null), defaultValue);
    }

    private double pathDouble(JsonNode node, String... path) {
        JsonNode current = node;
        for (String item : path) {
            current = current.path(item);
        }
        return current.isNumber() ? current.asDouble() : 0.0;
    }

    private String pathText(JsonNode node, String... path) {
        JsonNode current = node;
        for (String item : path) {
            current = current.path(item);
        }
        return current.asText("");
    }

    private JsonNode firstExisting(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull() && node.isObject()) {
                return node;
            }
        }
        return objectMapper.createObjectNode();
    }

    private String rgbaToHex(JsonNode color) {
        int r = color.path("r").isNumber() ? (int) Math.round(color.path("r").asDouble() * 255) : 0;
        int g = color.path("g").isNumber() ? (int) Math.round(color.path("g").asDouble() * 255) : 0;
        int b = color.path("b").isNumber() ? (int) Math.round(color.path("b").asDouble() * 255) : 0;
        return String.format("#%02X%02X%02X", clamp(r), clamp(g), clamp(b));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int parsePixel(String value) {
        return parsePixel(value, -1);
    }

    private int parsePixel(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace("px", "").trim();
        Matcher matcher = FIRST_NUMBER.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.group();
        }
        try {
            return (int) Math.round(Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean hasVisibleColor(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replace(" ", "");
        return !("transparent".equals(normalized) || "rgba(0,0,0,0)".equals(normalized));
    }

    private int rounded(double value) {
        return (int) Math.round(value);
    }

    private int firstInt(int... values) {
        for (int value : values) {
            if (value >= 0) {
                return value;
            }
        }
        return 0;
    }

    private double firstNumber(Double primary, double... fallbacks) {
        if (primary != null && primary > 0) {
            return primary;
        }
        for (double value : fallbacks) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @FunctionalInterface
    private interface TokenExtractor {
        int extract(JsonNode node);
    }
}
