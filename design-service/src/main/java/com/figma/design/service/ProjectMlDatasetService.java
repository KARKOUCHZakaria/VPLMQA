package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.ProjectMlDatasetResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectMlDatasetService {
    private static final double SAFE_MAPPING_CONFIDENCE = 0.70;

    private final WebClient projectServiceClient;
    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;

    public ProjectMlDatasetService(@Qualifier("projectServiceClient") WebClient projectServiceClient,
                                   MinIOService minIOService,
                                   ObjectMapper objectMapper) {
        this.projectServiceClient = projectServiceClient;
        this.minIOService = minIOService;
        this.objectMapper = objectMapper;
    }

    public ProjectMlDatasetResponse generate(UUID projectId) {
        return generate(projectId, null);
    }

    public ProjectMlDatasetResponse generate(UUID projectId, UUID pageId) {
        JsonNode project = getData("/api/v1/projects/" + projectId);
        JsonNode pagesNode = getData("/api/v1/projects/" + projectId + "/pages");
        Map<String, JsonNode> pagesById = new LinkedHashMap<>();
        for (JsonNode page : pagesNode) {
            pagesById.put(page.path("id").asText(), page);
        }
        String selectedPageKey = "";
        String selectedPageName = "";
        if (pageId != null) {
            JsonNode selectedPage = pagesById.get(pageId.toString());
            if (selectedPage == null || selectedPage.isMissingNode() || selectedPage.isNull()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected page does not belong to this project.");
            }
            selectedPageName = selectedPage.path("name").asText("page");
            selectedPageKey = matchKey(selectedPageName);
        }
        List<JsonNode> componentNodes = new ArrayList<>();
        if (selectedPageKey.isBlank()) {
            getData("/api/v1/projects/" + projectId + "/components").forEach(componentNodes::add);
        } else {
            for (Map.Entry<String, JsonNode> page : pagesById.entrySet()) {
                if (selectedPageKey.equals(matchKey(page.getValue().path("name").asText("")))) {
                    getData("/api/v1/projects/" + projectId + "/components/page/" + page.getKey())
                            .forEach(componentNodes::add);
                }
            }
        }
        List<JsonNode> figmaComponents = new ArrayList<>();
        List<JsonNode> webComponents = new ArrayList<>();
        for (JsonNode component : componentNodes) {
            if (!selectedPageKey.isBlank() && !selectedPageKey.equals(matchKey(pageName(component, pagesById)))) {
                continue;
            }
            String source = component.path("source").asText("");
            if ("FIGMA".equalsIgnoreCase(source)) {
                if (isComparable(component)) {
                    figmaComponents.add(component);
                }
            } else if ("WEB".equalsIgnoreCase(source)) {
                if (isComparable(component)) {
                    webComponents.add(component);
                }
            }
        }
        figmaComponents.sort(Comparator.comparing(this::visualOrder));
        webComponents.sort(Comparator.comparing(this::visualOrder));
        if (figmaComponents.isEmpty()) {
            String scope = selectedPageName.isBlank() ? "this project" : "page mapping '" + selectedPageName + "'";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Figma components found for " + scope + ". Extract the matching Figma page components first, or choose a page mapping that has FIGMA components.");
        }

        Set<String> matchedWebIds = new HashSet<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode figmaComponent : figmaComponents) {
            MatchCandidate match = bestWebMatch(figmaComponent, webComponents, matchedWebIds, pagesById);
            if (match == null || match.component() == null || match.component().isMissingNode()) continue;
            JsonNode webComponent = match.component();
            matchedWebIds.add(webComponent.path("id").asText());
            Map<String, Object> row = mlRow(figmaComponent, webComponent, pagesById);
            row.put("mapping_confidence", scoreToConfidence(match.score()));
            row.put("mapping_reason", match.reason());
            if (isUsableModelRow(row)) {
                rows.add(row);
            }
        }

        String projectName = project.path("name").asText("project");
        String artifactBase = selectedPageKey.isBlank()
                ? projectPath(projectName) + "/ml"
                : projectPath(projectName) + "/ml/pages/" + projectPath(selectedPageName);
        String jsonPath = artifactBase + "/design-token-comparison.json";
        String csvPath = artifactBase + "/design-token-comparison.csv";
        try {
            minIOService.uploadText(jsonPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows), "application/json");
            minIOService.uploadText(csvPath, toCsv(rows), "text/csv");
        } catch (Exception exception) {
            throw new RuntimeException("Failed to store ML dataset artifacts: " + exception.getMessage(), exception);
        }

        return new ProjectMlDatasetResponse(projectId, rows.size(), jsonPath, csvPath, rows);
    }

    private JsonNode getData(String path) {
        JsonNode envelope = projectServiceClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (envelope == null || !envelope.path("success").asBoolean(false)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Project service request failed: " + path);
        }
        return envelope.path("data");
    }

    private MatchCandidate bestWebMatch(JsonNode figmaComponent, List<JsonNode> webComponents, Set<String> alreadyMatched, Map<String, JsonNode> pagesById) {
        String figmaPageKey = matchKey(pageName(figmaComponent, pagesById));
        String figmaNameKey = matchKey(figmaComponent.path("canonicalName").asText());
        String figmaRoleKey = matchKey(figmaComponent.path("semanticRole").asText());
        String figmaMeaningKey = matchKey(figmaComponent.path("functionalMeaning").asText());
        String figmaTypeKey = matchKey(firstNonBlank(
                figmaComponent.path("componentType").asText(null),
                figmaComponent.path("elementType").asText(null),
                figmaComponent.path("type").asText(null)
        ));
        String figmaVisualRole = visualRole(figmaComponent);
        JsonNode best = null;
        int bestScore = -1;
        String bestReason = "";
        for (JsonNode webComponent : webComponents) {
            String id = webComponent.path("id").asText();
            if (!id.isBlank() && alreadyMatched.contains(id)) {
                continue;
            }
            String webPageKey = matchKey(pageName(webComponent, pagesById));
            if (!figmaPageKey.isBlank() && !webPageKey.isBlank() && !figmaPageKey.equals(webPageKey)) {
                continue;
            }
            String webNameKey = matchKey(webComponent.path("canonicalName").asText());
            String webRoleKey = matchKey(webComponent.path("semanticRole").asText());
            String webMeaningKey = matchKey(webComponent.path("functionalMeaning").asText());
            String webTypeKey = matchKey(firstNonBlank(
                    webComponent.path("componentType").asText(null),
                    webComponent.path("elementType").asText(null),
                    webComponent.path("type").asText(null)
            ));
            String webVisualRole = visualRole(webComponent);
            if (!rolesCompatible(figmaVisualRole, webVisualRole)) {
                continue;
            }
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (!figmaNameKey.isBlank() && figmaNameKey.equals(webNameKey)) {
                score += 80;
                reasons.add("same normalized name");
            } else if (!figmaNameKey.isBlank() && !webNameKey.isBlank()
                    && (figmaNameKey.contains(webNameKey) || webNameKey.contains(figmaNameKey))) {
                score += 45;
                reasons.add("similar normalized name");
            }
            score += addReasonedSimilarity(reasons, "semantic role", figmaRoleKey, webRoleKey, 55, 30);
            score += addReasonedSimilarity(reasons, "functional meaning", figmaMeaningKey, webMeaningKey, 35, 20);
            score += addReasonedSimilarity(reasons, "technical type", figmaTypeKey, webTypeKey, 25, 10);
            int roleScore = figmaVisualRole.equals(webVisualRole) ? 35 : 15;
            score += roleScore;
            reasons.add("compatible visual role: " + figmaVisualRole + "/" + webVisualRole);
            int textScore = Math.round((float) tokenSimilarity(componentSignals(figmaComponent), componentSignals(webComponent)) * 45);
            if (textScore > 0) {
                score += textScore;
                reasons.add("shared labels/tokens");
            }
            int geometryScore = geometryScore(figmaComponent, webComponent);
            if (geometryScore > 0) {
                score += geometryScore;
                reasons.add("close visual position/size");
            }
            String locatorKey = matchKey(firstNonBlank(
                    webComponent.path("htmlId").asText(null),
                    webComponent.path("testIdentifier").asText(null),
                    webComponent.path("cssSelector").asText(null)
            ));
            if (!figmaNameKey.isBlank() && !locatorKey.isBlank()
                    && (figmaNameKey.contains(locatorKey) || locatorKey.contains(figmaNameKey))) {
                score += 30;
                reasons.add("locator contains design name");
            }
            if (!figmaRoleKey.isBlank() && !locatorKey.isBlank()
                    && (figmaRoleKey.contains(locatorKey) || locatorKey.contains(figmaRoleKey))) {
                score += 20;
                reasons.add("locator contains role");
            }
            if (score > bestScore) {
                bestScore = score;
                best = webComponent;
                bestReason = String.join(", ", reasons);
            }
        }
        return bestScore >= 70 ? new MatchCandidate(best, bestScore, bestReason) : null;
    }

    private int similarityScore(String left, String right, int exact, int partial) {
        if (left.isBlank() || right.isBlank()) return 0;
        if (left.equals(right)) return exact;
        return left.contains(right) || right.contains(left) ? partial : 0;
    }

    private int addReasonedSimilarity(List<String> reasons, String label, String left, String right, int exact, int partial) {
        int score = similarityScore(left, right, exact, partial);
        if (score == exact) {
            reasons.add("same " + label);
        } else if (score == partial) {
            reasons.add("similar " + label);
        }
        return score;
    }

    private Map<String, Object> mlRow(JsonNode figmaComponent, JsonNode webComponent, Map<String, JsonNode> pagesById) {
        Map<String, Object> row = new LinkedHashMap<>();
        String uniqueName = componentName(figmaComponent, webComponent, pagesById);
        row.put("component", uniqueName);
        row.put("figma_color", normalizeColor(firstToken(figmaComponent, "color", "backgroundColor", "strokeColor")));
        row.put("code_color", normalizeColor(firstToken(webComponent, "color", "backgroundColor", "borderColor")));
        row.put("figma_spacing", spacingToken(figmaComponent));
        row.put("code_spacing", spacingToken(webComponent));
        row.put("figma_font_size", numberToken(token(figmaComponent, "fontSize")));
        row.put("code_font_size", numberToken(token(webComponent, "fontSize")));
        row.put("figma_font_weight", modelFontWeight(figmaComponent));
        row.put("code_font_weight", modelFontWeight(webComponent));
        row.put("figma_border_radius", numberToken(token(figmaComponent, "borderRadius")));
        row.put("code_border_radius", numberToken(token(webComponent, "borderRadius")));
        row.put("figma_width", numberToken(box(figmaComponent, "width")));
        row.put("code_width", numberToken(box(webComponent, "width")));
        row.put("figma_height", numberToken(box(figmaComponent, "height")));
        row.put("code_height", numberToken(box(webComponent, "height")));
        row.put("figma_x", numberToken(box(figmaComponent, "x")));
        row.put("figma_y", numberToken(box(figmaComponent, "y")));
        row.put("code_x", numberToken(box(webComponent, "x")));
        row.put("code_y", numberToken(box(webComponent, "y")));
        row.put("uniqueName", uniqueName);
        row.put("usage", usageFor(uniqueName, figmaComponent, webComponent));
        row.put("figma_page", pageName(figmaComponent, pagesById));
        row.put("web_page", webComponent == null ? "" : pageName(webComponent, pagesById));
        row.put("web_locator", locator(webComponent));
        row.put("figma_component_id", figmaComponent.path("id").asText(""));
        row.put("web_component_id", webComponent == null ? "" : webComponent.path("id").asText(""));
        row.put("mapping_confidence", mappingConfidence(figmaComponent, webComponent));
        return row;
    }

    private String componentName(JsonNode figmaComponent, JsonNode webComponent, Map<String, JsonNode> pagesById) {
        String page = projectPath(firstNonBlank(pageName(webComponent, pagesById), pageName(figmaComponent, pagesById), "page"));
        String role = componentRole(figmaComponent, webComponent);
        String label = componentLabel(figmaComponent, webComponent, role);
        return compactName(page + "." + label + "_" + role, 80);
    }

    private String componentRole(JsonNode figmaComponent, JsonNode webComponent) {
        String tag = text(webComponent, "componentType").toLowerCase();
        if (tag.isBlank()) tag = text(webComponent, "elementType").toLowerCase();
        if (tag.isBlank()) tag = text(webComponent, "type").toLowerCase();
        String name = (text(webComponent, "canonicalName") + " " + text(figmaComponent, "canonicalName")
                + " " + text(webComponent, "semanticRole") + " " + text(figmaComponent, "semanticRole")
                + " " + text(webComponent, "functionalMeaning") + " " + text(figmaComponent, "functionalMeaning")).toLowerCase();
        String locator = locator(webComponent).toLowerCase();
        String fieldIdentity = (text(webComponent, "htmlId") + " " + text(webComponent, "testIdentifier") + " "
                + attr(webComponent, "name") + " " + attr(webComponent, "placeholder") + " "
                + text(webComponent, "canonicalName")).toLowerCase();
        if (fieldIdentity.contains("email") || fieldIdentity.contains("username") || fieldIdentity.contains("identifier")) return "identifier_input";
        if (fieldIdentity.contains("password")) return "password_input";
        if (fieldIdentity.contains("remember") || fieldIdentity.contains("checkbox")) return "checkbox";
        if (fieldIdentity.contains("forgot")) return "forgot_password_link";
        if (fieldIdentity.contains("sign") || fieldIdentity.contains("login") || fieldIdentity.contains("submit") || fieldIdentity.contains("connexion")) return "submit_button";
        String all = name + " " + locator;
        if (all.contains("email") || all.contains("username") || all.contains("identifier")) return "identifier_input";
        if (all.contains("sign") || all.contains("login") || all.contains("submit") || all.contains("connexion")) return "submit_button";
        if (all.contains("password")) return "password_input";
        if (all.contains("checkbox") || all.contains("remember")) return "checkbox";
        if (all.contains("forgot")) return "forgot_password_link";
        if (tag.contains("input")) return "input";
        if (tag.contains("button")) return "button";
        if (tag.contains("select")) return "select";
        if (tag.contains("label")) return "label";
        if (tag.contains("form")) return "form";
        if (tag.equals("a")) return "link";
        return "component";
    }

    private String componentLabel(JsonNode figmaComponent, JsonNode webComponent, String role) {
        String raw = firstNonBlank(
                text(webComponent, "htmlId"),
                text(webComponent, "testIdentifier"),
                attr(webComponent, "name"),
                attr(webComponent, "placeholder"),
                text(webComponent, "semanticRole"),
                text(figmaComponent, "semanticRole"),
                figmaComponent.path("canonicalName").asText(null),
                text(webComponent, "canonicalName"),
                role
        );
        String cleaned = raw.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (cleaned.isBlank() || cleaned.length() > 42) {
            cleaned = role.replaceAll("[^a-z0-9]+", "_");
        }
        return cleaned;
    }

    private boolean isComparable(JsonNode component) {
        JsonNode box = component.path("boundingBox");
        boolean hasGeometry = numberToken(box.path("width").asText()) > 0
                && numberToken(box.path("height").asText()) > 0;
        JsonNode css = component.path("cssProperties");
        boolean hasTokens = List.of(
                "color", "backgroundColor", "borderColor", "spacing", "padding", "gap",
                "fontSize", "fontWeight", "borderRadius"
        ).stream().anyMatch(key -> !css.path(key).asText("").isBlank());
        return hasGeometry || hasTokens;
    }

    private boolean isUsableModelRow(Map<String, Object> row) {
        String component = String.valueOf(row.getOrDefault("component", ""));
        return !component.isBlank() && !component.contains(".component_component")
                && Number.class.isAssignableFrom(row.getOrDefault("mapping_confidence", 0.0).getClass())
                && ((Number) row.getOrDefault("mapping_confidence", 0.0)).doubleValue() >= SAFE_MAPPING_CONFIDENCE
                && numberToken(row.get("figma_width")) > 0
                && numberToken(row.get("code_width")) > 0
                && numberToken(row.get("figma_height")) > 0
                && numberToken(row.get("code_height")) > 0;
    }

    private double mappingConfidence(JsonNode figma, JsonNode web) {
        if (web == null || web.isMissingNode() || web.isNull()) return 0.0;
        String figmaName = matchKey(figma.path("canonicalName").asText(""));
        String webName = matchKey(web.path("canonicalName").asText(""));
        if (!figmaName.isBlank() && figmaName.equals(webName)) return 1.0;
        String figmaRole = matchKey(figma.path("semanticRole").asText(""));
        String webRole = matchKey(web.path("semanticRole").asText(""));
        if (!figmaRole.isBlank() && figmaRole.equals(webRole)) return 0.9;
        if (!figmaName.isBlank() && !webName.isBlank() && (figmaName.contains(webName) || webName.contains(figmaName))) return 0.8;
        if (!figmaRole.isBlank() && !webRole.isBlank()
                && (figmaRole.contains(webRole) || webRole.contains(figmaRole))) return 0.7;
        return 0.5;
    }

    private String toCsv(List<Map<String, Object>> rows) {
        List<String> columns = List.of(
                "component", "figma_color", "code_color", "figma_spacing", "code_spacing",
                "figma_font_size", "code_font_size", "figma_font_weight", "code_font_weight",
                "figma_border_radius", "code_border_radius", "figma_width", "code_width",
                "figma_height", "code_height"
        );
        StringBuilder csv = new StringBuilder(String.join(",", columns)).append("\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    csv.append(",");
                }
                csv.append(csvCell(row.get(columns.get(i))));
            }
            csv.append("\n");
        }
        return csv.toString();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String usageFor(String uniqueName, JsonNode figmaComponent, JsonNode webComponent) {
        String role = componentRole(figmaComponent, webComponent);
        String locator = locator(webComponent);
        String action = switch (role) {
            case "identifier_input" -> "Enter the account identifier before submitting the login workflow";
            case "password_input" -> "Enter the password after the account identifier is filled";
            case "checkbox" -> "Toggle this option when the scenario needs the related preference";
            case "forgot_password_link" -> "Open the password recovery flow from the login screen";
            case "submit_button" -> "Click this button after the required fields are completed";
            case "select" -> "Choose the required option before continuing the workflow";
            case "link" -> "Navigate through this link when the scenario requires it";
            case "form" -> "Use this form as the group that contains the page inputs and submit action";
            default -> "Use this component during visual comparison and end-to-end execution";
        };
        if (locator.isBlank()) {
            return action + ".";
        }
        return action + ". Preferred E2E locator: " + locator + ".";
    }

    private String locator(JsonNode webComponent) {
        if (webComponent == null || webComponent.isMissingNode() || webComponent.isNull()) {
            return "";
        }
        return firstNonBlank(
                webComponent.path("htmlId").asText(null),
                webComponent.path("testIdentifier").asText(null),
                webComponent.path("cssSelector").asText(null),
                webComponent.path("xpath").asText(null)
        );
    }

    private String attr(JsonNode component, String key) {
        if (component == null || component.isMissingNode() || component.isNull()) {
            return "";
        }
        return component.path("attributes").path(key).asText("");
    }

    private String token(JsonNode component, String key) {
        if (component == null || component.isMissingNode() || component.isNull()) {
            return "";
        }
        JsonNode value = component.path("cssProperties").path(key);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private String firstToken(JsonNode component, String... keys) {
        if (component == null || component.isMissingNode() || component.isNull()) {
            return "";
        }
        for (String key : keys) {
            String value = token(component, key);
            if (!value.isBlank() && !"transparent".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return "";
    }

    private int spacingToken(JsonNode component) {
        return maxPositive(
                numberToken(token(component, "spacing")),
                numberToken(token(component, "padding")),
                numberToken(token(component, "paddingTop")),
                numberToken(token(component, "paddingRight")),
                numberToken(token(component, "paddingBottom")),
                numberToken(token(component, "paddingLeft")),
                numberToken(token(component, "gap"))
        );
    }

    private Object box(JsonNode component, String key) {
        if (component == null || component.isMissingNode() || component.isNull()) {
            return 0;
        }
        JsonNode value = component.path("boundingBox").path(key);
        return value.isMissingNode() || value.isNull() ? 0 : value.asText();
    }

    private int numberToken(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim();
            Matcher matcher = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(text);
            if (!matcher.find()) {
                return 0;
            }
            return (int) Math.round(Double.parseDouble(matcher.group()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String normalizeColor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim();
        if ("transparent".equalsIgnoreCase(text)) {
            return "transparent";
        }
        if (text.startsWith("#")) {
            String hex = text.substring(1).trim();
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() >= 6) {
                return "#" + hex.substring(0, 6).toUpperCase();
            }
            return text.toUpperCase();
        }
        Matcher matcher = Pattern.compile("rgba?\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            String[] parts = matcher.group(1).split(",");
            if (parts.length >= 3) {
                double alpha = parts.length >= 4 ? alphaValue(parts[3]) : 1.0;
                if (alpha <= 0.0) {
                    return "transparent";
                }
                return String.format("#%02X%02X%02X",
                        colorChannel(parts[0]), colorChannel(parts[1]), colorChannel(parts[2]));
            }
        }
        return text;
    }

    private int colorChannel(String value) {
        String text = value == null ? "" : value.trim();
        try {
            double parsed = text.endsWith("%")
                    ? Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 2.55
                    : Double.parseDouble(text);
            return Math.max(0, Math.min(255, (int) Math.round(parsed)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double alphaValue(String value) {
        String text = value == null ? "" : value.trim();
        try {
            if (text.endsWith("%")) {
                return Double.parseDouble(text.substring(0, text.length() - 1).trim()) / 100.0;
            }
            return Double.parseDouble(text);
        } catch (Exception ignored) {
            return 1.0;
        }
    }

    private String normalizeFontWeight(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim().toLowerCase();
        return switch (text) {
            case "normal", "regular" -> "400";
            case "medium" -> "500";
            case "semibold", "semi-bold", "demibold", "demi-bold" -> "600";
            case "bold" -> "700";
            default -> {
                int numeric = numberToken(text);
                yield numeric > 0 ? String.valueOf(numeric) : value.trim();
            }
        };
    }

    private String modelFontWeight(JsonNode component) {
        String weight = normalizeFontWeight(token(component, "fontWeight"));
        if (!weight.isBlank()) return weight;
        String role = component == null ? "" : component.path("semanticRole").asText("").toLowerCase();
        return List.of("checkbox", "radio").contains(role) ? "400" : "";
    }

    private int firstPositive(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private int maxPositive(int... values) {
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private String text(JsonNode node, String key) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.path(key).asText("");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String matchKey(String value) {
        return safe(value).toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String compactName(String value, int maxLength) {
        String normalized = firstNonBlank(value, "component").trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        String hash = Integer.toHexString(normalized.hashCode());
        int prefixLength = maxLength - hash.length() - 1;
        return normalized.substring(0, Math.max(1, prefixLength)).trim() + "-" + hash;
    }

    private String projectPath(String value) {
        String sanitized = firstNonBlank(value, "project").trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "project" : sanitized;
    }

    private String pageName(JsonNode component, Map<String, JsonNode> pagesById) {
        String nested = component.path("page").path("name").asText("");
        if (!nested.isBlank()) {
            return nested;
        }
        String pageId = component.path("pageId").asText("");
        JsonNode page = pagesById.get(pageId);
        return page == null ? "" : page.path("name").asText("");
    }

    private String visualOrder(JsonNode component) {
        return String.format("%08d:%08d:%s",
                numberToken(box(component, "y")),
                numberToken(box(component, "x")),
                safe(component.path("canonicalName").asText()));
    }

    private String componentSignals(JsonNode component) {
        if (component == null || component.isMissingNode() || component.isNull()) return "";
        JsonNode css = component.path("cssProperties");
        JsonNode attrs = component.path("attributes");
        return String.join(" ",
                component.path("canonicalName").asText(""),
                component.path("semanticRole").asText(""),
                component.path("functionalMeaning").asText(""),
                component.path("componentType").asText(""),
                component.path("elementType").asText(""),
                component.path("type").asText(""),
                component.path("htmlId").asText(""),
                component.path("testIdentifier").asText(""),
                component.path("cssSelector").asText(""),
                component.path("xpath").asText(""),
                css.path("text").asText(""),
                css.path("role").asText(""),
                css.path("type").asText(""),
                attrs.path("name").asText(""),
                attrs.path("placeholder").asText(""),
                attrs.path("aria-label").asText(""),
                attrs.path("title").asText("")
        ).toLowerCase();
    }

    private String visualRole(JsonNode component) {
        String signals = componentSignals(component);
        if (signals.contains("checkbox")) return "checkbox";
        if (signals.contains("radio")) return "radio";
        if (signals.contains("input") || signals.contains("field") || signals.contains("placeholder")
                || signals.contains("password") || signals.contains("email") || signals.contains("search")) {
            return "input";
        }
        if (signals.contains("button") || signals.contains("submit") || signals.contains("click")
                || signals.contains("save") || signals.contains("confirm") || signals.contains("cancel")
                || signals.contains("delete") || signals.contains("icon")) {
            return "button";
        }
        if (signals.contains("link") || signals.contains("href") || signals.contains("navigation")
                || signals.contains("menu")) {
            return "navigation";
        }
        if (signals.contains("title") || signals.contains("heading") || signals.contains("label")
                || signals.contains("text")) {
            return "text";
        }
        return "container";
    }

    private boolean rolesCompatible(String figmaRole, String webRole) {
        if (figmaRole.equals(webRole)) return true;
        if ("container".equals(figmaRole) || "container".equals(webRole)) return true;
        if (Set.of(figmaRole, webRole).stream().allMatch(Set.of("text", "navigation")::contains)) return true;
        return Set.of(figmaRole, webRole).stream().allMatch(Set.of("button", "navigation")::contains);
    }

    private double tokenSimilarity(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        for (String part : safe(value).toLowerCase().replaceAll("[^a-z0-9]+", " ").split("\\s+")) {
            if (part.length() > 1 && !Set.of("div", "span", "mat", "mdc", "ng", "css").contains(part)) {
                result.add(part);
            }
        }
        return result;
    }

    private int geometryScore(JsonNode figma, JsonNode web) {
        double figmaWidth = numberToken(box(figma, "width"));
        double figmaHeight = numberToken(box(figma, "height"));
        double webWidth = numberToken(box(web, "width"));
        double webHeight = numberToken(box(web, "height"));
        if (figmaWidth <= 0 || figmaHeight <= 0 || webWidth <= 0 || webHeight <= 0) return 0;

        double figmaCenterX = numberToken(box(figma, "x")) + figmaWidth / 2.0;
        double figmaCenterY = numberToken(box(figma, "y")) + figmaHeight / 2.0;
        double webCenterX = numberToken(box(web, "x")) + webWidth / 2.0;
        double webCenterY = numberToken(box(web, "y")) + webHeight / 2.0;
        double distance = Math.hypot(figmaCenterX - webCenterX, figmaCenterY - webCenterY);
        double positionScore = Math.max(0.0, 1.0 - Math.min(1.0, distance / 320.0)) * 35.0;
        double widthRatio = Math.min(figmaWidth, webWidth) / Math.max(figmaWidth, webWidth);
        double heightRatio = Math.min(figmaHeight, webHeight) / Math.max(figmaHeight, webHeight);
        double sizeScore = ((widthRatio + heightRatio) / 2.0) * 20.0;
        return (int) Math.round(positionScore + sizeScore);
    }

    private double scoreToConfidence(int score) {
        return Math.max(0.0, Math.min(1.0, score / 140.0));
    }

    private record MatchCandidate(JsonNode component, int score, String reason) {}
}
