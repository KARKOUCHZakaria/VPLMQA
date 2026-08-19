package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service for discovering and indexing components from folder structure
 * 
 * Purpose:
 * - Read Figma component files from folders
 * - Read Web component files from folders
 * - Extract metadata (name, purpose, type, etc.)
 * - Create lookup index for E2E matching
 * - Help match Figma components to Web components
 */
@Service
@Slf4j
public class ComponentDiscoveryService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String FIGMA_COMPONENTS_FOLDER = "figma-components";
    private static final String WEB_COMPONENTS_FOLDER = "web-components";

    /**
     * Component metadata extracted from files
     * Used for identifying and matching components
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentMetadata {
        private String componentId;           // Unique ID (filename or hash)
        private String componentName;          // Human-readable name (e.g., "Login Button")
        private String componentType;          // Type (Button, Card, Form, etc.)
        private String functionalRole;         // What it does (e.g., "Submits login form")
        private String description;            // Detailed description
        private Map<String, Object> properties; // Button color, size, text, etc.
        private String filePath;               // Where this component is stored
        private String sourceType;             // FIGMA or WEB
        private Long pageId;                   // Which page this belongs to
        private String pageName;               // Page name
    }

    /**
     * Matched component pair (Figma ↔ Web)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentMatch {
        private ComponentMetadata figmaComponent;
        private ComponentMetadata webComponent;
        private double matchScore;             // 0-100% how confident the match is
        private List<String> matchReasons;     // Why they match
        private List<String> differences;      // Differences found
    }

    /**
     * Discover all Figma components in a page folder
     * 
     * Example structure:
     * pages/
     *   login-page/
     *     figma-components/
     *       button-submit.json
     *       input-email.json
     *       label-password.json
     * 
     * @param pageFolderPath Path to page folder (e.g., "pages/login-page")
     * @return List of discovered Figma components with metadata
     */
    public List<ComponentMetadata> discoverFigmaComponents(String pageFolderPath) {
        List<ComponentMetadata> components = new ArrayList<>();
        Path figmaComponentsPath = Paths.get(pageFolderPath, FIGMA_COMPONENTS_FOLDER);

        try {
            if (!Files.exists(figmaComponentsPath)) {
                log.warn("Figma components folder not found: {}", figmaComponentsPath);
                return components;
            }

            Files.list(figmaComponentsPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            ComponentMetadata metadata = extractComponentMetadata(path, "FIGMA", pageFolderPath);
                            components.add(metadata);
                            log.debug("Discovered Figma component: {}", metadata.getComponentName());
                        } catch (IOException e) {
                            log.error("Error processing figma component file: {}", path, e);
                        }
                    });

        } catch (IOException e) {
            log.error("Error discovering figma components in: {}", figmaComponentsPath, e);
        }

        return components;
    }

    /**
     * Discover all Web components in a page folder
     * 
     * Example structure:
     * pages/
     *   login-page/
     *     web-components/
     *       button-submit.json
     *       input-email.json
     *       label-password.json
     * 
     * @param pageFolderPath Path to page folder (e.g., "pages/login-page")
     * @return List of discovered Web components with metadata
     */
    public List<ComponentMetadata> discoverWebComponents(String pageFolderPath) {
        List<ComponentMetadata> components = new ArrayList<>();
        Path webComponentsPath = Paths.get(pageFolderPath, WEB_COMPONENTS_FOLDER);

        try {
            if (!Files.exists(webComponentsPath)) {
                log.warn("Web components folder not found: {}", webComponentsPath);
                return components;
            }

            Files.list(webComponentsPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            ComponentMetadata metadata = extractComponentMetadata(path, "WEB", pageFolderPath);
                            components.add(metadata);
                            log.debug("Discovered Web component: {}", metadata.getComponentName());
                        } catch (IOException e) {
                            log.error("Error processing web component file: {}", path, e);
                        }
                    });

        } catch (IOException e) {
            log.error("Error discovering web components in: {}", webComponentsPath, e);
        }

        return components;
    }

    /**
     * Extract metadata from a single component JSON file
     */
    private ComponentMetadata extractComponentMetadata(Path filePath, String sourceType, String pageFolderPath) throws IOException {
        JsonNode componentJson = objectMapper.readTree(Files.readString(filePath));

        String componentName = extractStringField(componentJson, "componentName", "name", "figmaNodeName");
        String componentType = extractStringField(componentJson, "componentType", "type", "figmaNodeType");
        String functionalRole = extractStringField(componentJson, "functionalRole", "role", "purpose");
        String description = extractStringField(componentJson, "description", "componentDescription");

        // Extract properties (specific attributes of the component)
        Map<String, Object> properties = extractProperties(componentJson);

        // Extract page info
        String pageName = new File(pageFolderPath).getName();
        long pageId = hashString(pageName);

        return ComponentMetadata.builder()
                .componentId(filePath.getFileName().toString().replace(".json", ""))
                .componentName(componentName)
                .componentType(componentType)
                .functionalRole(functionalRole)
                .description(description)
                .properties(properties)
                .filePath(filePath.toString())
                .sourceType(sourceType)
                .pageId(pageId)
                .pageName(pageName)
                .build();
    }

    /**
     * Try to extract string field from JSON, checking multiple possible field names
     */
    private String extractStringField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                JsonNode field = node.get(fieldName);
                if (field.isTextual()) {
                    return field.asText();
                }
            }
        }
        return null;
    }

    /**
     * Extract component-specific properties from JSON
     */
    private Map<String, Object> extractProperties(JsonNode componentJson) {
        Map<String, Object> properties = new HashMap<>();

        // Common properties to extract
        String[] propertyNames = {
                "htmlTag", "htmlClass", "htmlID", "cssSelector",
                "width", "height", "positionX", "positionY",
                "color", "backgroundColor", "text", "textContent",
                "attributes", "nodeWidth", "nodeHeight", "figmaNodeType"
        };

        for (String propName : propertyNames) {
            if (componentJson.has(propName)) {
                JsonNode prop = componentJson.get(propName);
                if (prop.isValueNode()) {
                    properties.put(propName, prop.asText());
                }
            }
        }

        return properties;
    }

    /**
     * Match Figma components with Web components on the same page
     * 
     * Matching strategy:
     * 1. Same component name → High confidence (90%+)
     * 2. Same component type + similar name → Medium confidence (70-80%)
     * 3. Same functional role → Lower confidence (60-70%)
     * 4. Manual mapping needed → 0% (no match found)
     * 
     * @param figmaComponents Figma components discovered
     * @param webComponents Web components discovered
     * @return List of component matches
     */
    public List<ComponentMatch> matchComponents(List<ComponentMetadata> figmaComponents, 
                                                 List<ComponentMetadata> webComponents) {
        List<ComponentMatch> matches = new ArrayList<>();

        for (ComponentMetadata figmaComp : figmaComponents) {
            // Try to find matching web component
            ComponentMetadata bestMatch = null;
            double bestScore = 0;
            List<String> bestReasons = new ArrayList<>();

            for (ComponentMetadata webComp : webComponents) {
                double score = 0;
                List<String> reasons = new ArrayList<>();

                // Strategy 1: Exact or near-exact name match (highest priority)
                if (namesMatch(figmaComp.getComponentName(), webComp.getComponentName())) {
                    score += 50;
                    reasons.add("Component names match exactly");
                }

                // Strategy 2: Type match
                if (typesMatch(figmaComp.getComponentType(), webComp.getComponentType())) {
                    score += 30;
                    reasons.add("Component types match (" + figmaComp.getComponentType() + ")");
                }

                // Strategy 3: Functional role match
                if (rolesMatch(figmaComp.getFunctionalRole(), webComp.getFunctionalRole())) {
                    score += 20;
                    reasons.add("Functional roles match");
                }

                // Normalize score to 0-100
                double normalizedScore = (score / 100.0) * 100;

                if (normalizedScore > bestScore) {
                    bestScore = normalizedScore;
                    bestMatch = webComp;
                    bestReasons = reasons;
                }
            }

            // Only create match if score is reasonable (> 40%)
            if (bestMatch != null && bestScore > 40) {
                List<String> differences = findDifferences(figmaComp, bestMatch);

                matches.add(ComponentMatch.builder()
                        .figmaComponent(figmaComp)
                        .webComponent(bestMatch)
                        .matchScore(bestScore)
                        .matchReasons(bestReasons)
                        .differences(differences)
                        .build());

                log.info("Matched: {} (Figma) ↔ {} (Web) - {}% confidence",
                         figmaComp.getComponentName(), bestMatch.getComponentName(), (int)bestScore);
            }
        }

        return matches;
    }

    /**
     * Check if component names are similar
     */
    private boolean namesMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String n1 = normalize(name1);
        String n2 = normalize(name2);
        return n1.equals(n2) || n1.contains(n2) || n2.contains(n1);
    }

    /**
     * Check if component types are similar
     */
    private boolean typesMatch(String type1, String type2) {
        if (type1 == null || type2 == null) return false;
        return normalize(type1).equalsIgnoreCase(normalize(type2));
    }

    /**
     * Check if functional roles are similar
     */
    private boolean rolesMatch(String role1, String role2) {
        if (role1 == null || role2 == null) return false;
        String r1 = normalize(role1);
        String r2 = normalize(role2);
        // Check if they share common keywords
        return r1.contains(r2) || r2.contains(r1);
    }

    /**
     * Normalize string for comparison (lowercase, remove special chars)
     */
    private String normalize(String str) {
        if (str == null) return "";
        return str.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }

    /**
     * Find differences between matched components
     */
    private List<String> findDifferences(ComponentMetadata figmaComp, ComponentMetadata webComp) {
        List<String> differences = new ArrayList<>();

        // Compare names
        if (!Objects.equals(figmaComp.getComponentName(), webComp.getComponentName())) {
            differences.add("Name differs: Figma='" + figmaComp.getComponentName() + 
                          "' vs Web='" + webComp.getComponentName() + "'");
        }

        // Compare types
        if (!Objects.equals(figmaComp.getComponentType(), webComp.getComponentType())) {
            differences.add("Type differs: Figma='" + figmaComp.getComponentType() + 
                          "' vs Web='" + webComp.getComponentType() + "'");
        }

        // Compare dimensions
        Object figmaWidth = figmaComp.getProperties().get("nodeWidth");
        Object webWidth = webComp.getProperties().get("width");
        if (figmaWidth != null && webWidth != null && !figmaWidth.equals(webWidth)) {
            differences.add("Width differs: Figma=" + figmaWidth + " vs Web=" + webWidth);
        }

        return differences;
    }

    /**
     * Generate simple hash from string
     */
    private long hashString(String str) {
        return str == null ? 0 : str.hashCode();
    }

    /**
     * Create a human-readable component identification guide
     */
    public String generateComponentGuide(List<ComponentMatch> matches, String pageName) {
        StringBuilder guide = new StringBuilder();
        guide.append("\n").append("=".repeat(80)).append("\n");
        guide.append("COMPONENT IDENTIFICATION GUIDE - ").append(pageName).append("\n");
        guide.append("=".repeat(80)).append("\n\n");

        guide.append("Page: ").append(pageName).append("\n");
        guide.append("Total Components Found: ").append(matches.size()).append("\n\n");

        for (int i = 0; i < matches.size(); i++) {
            ComponentMatch match = matches.get(i);
            ComponentMetadata figma = match.getFigmaComponent();
            ComponentMetadata web = match.getWebComponent();

            guide.append("─".repeat(80)).append("\n");
            guide.append(String.format("COMPONENT #%d - Confidence: %d%%\n", i + 1, (int)match.getMatchScore()));
            guide.append("─".repeat(80)).append("\n\n");

            guide.append("📐 FIGMA DESIGN:\n");
            guide.append("   Name:        ").append(figma.getComponentName()).append("\n");
            guide.append("   Type:        ").append(figma.getComponentType()).append("\n");
            guide.append("   Role:        ").append(figma.getFunctionalRole()).append("\n");
            guide.append("   File:        ").append(figma.getComponentId()).append("\n\n");

            guide.append("💻 WEB IMPLEMENTATION:\n");
            guide.append("   Name:        ").append(web.getComponentName()).append("\n");
            guide.append("   Type:        ").append(web.getComponentType()).append("\n");
            guide.append("   Role:        ").append(web.getFunctionalRole()).append("\n");
            guide.append("   File:        ").append(web.getComponentId()).append("\n");
            if (web.getProperties().containsKey("cssSelector")) {
                guide.append("   CSS:         ").append(web.getProperties().get("cssSelector")).append("\n");
            }
            if (web.getProperties().containsKey("htmlID")) {
                guide.append("   ID:          ").append(web.getProperties().get("htmlID")).append("\n");
            }
            guide.append("\n");

            guide.append("✅ MATCH REASONS:\n");
            for (String reason : match.getMatchReasons()) {
                guide.append("   • ").append(reason).append("\n");
            }
            guide.append("\n");

            if (!match.getDifferences().isEmpty()) {
                guide.append("⚠️  DIFFERENCES:\n");
                for (String diff : match.getDifferences()) {
                    guide.append("   • ").append(diff).append("\n");
                }
                guide.append("\n");
            }

            guide.append("🧪 FOR E2E TEST USE THIS:\n");
            guide.append("   ComponentId: ").append(web.getComponentId()).append("\n");
            guide.append("   Role:        ").append(web.getFunctionalRole()).append("\n");
            guide.append("   Description: ").append(web.getDescription()).append("\n");
            guide.append("\n");
        }

        guide.append("=".repeat(80)).append("\n\n");
        return guide.toString();
    }
}
