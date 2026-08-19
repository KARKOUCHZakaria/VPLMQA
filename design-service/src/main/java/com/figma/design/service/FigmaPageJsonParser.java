package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.figma.design.model.FigmaComponent;
import com.figma.design.model.Page;
import com.figma.design.repository.FigmaComponentRepository;
import com.figma.design.repository.PageRepository;
import com.figma.design.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing Figma page JSON and extracting individual component data
 * Generates component JSON files from raw Figma page export format or database entities
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaPageJsonParser {

    private final ObjectMapper objectMapper;
    private final MinIOService minIOService;
    private final FigmaComponentRepository figmaComponentRepository;
    private final PageRepository pageRepository;

    /**
     * Generate component JSON files directly from page ID (database entities)
     * Fetches page and all its components from database and generates JSON
     * @param pageId Page entity ID from database
     * @return List of MinIO paths for stored components
     */
    public List<String> generateComponentsFromPageId(Long pageId) {
        List<String> storedComponentPaths = new ArrayList<>();
        
        try {
            // Fetch page entity from database
            Page page = pageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Page not found with ID: " + pageId));
            
            // Fetch all components for this page
            List<FigmaComponent> pageComponents = figmaComponentRepository.findByPage_Id(pageId);
            
            if (pageComponents.isEmpty()) {
                log.warn("No components found for page ID: {}", pageId);
                return storedComponentPaths;
            }
            
            // Sanitize project and page names for MinIO paths
            String projectName = FileNameSanitizer.sanitize(page.getProject().getName());
            String pageName = FileNameSanitizer.sanitize(page.getName());
            
            log.info("Generating {} components from database for page: {} (ID: {})", 
                pageComponents.size(), page.getName(), pageId);
            
            // Generate and store each component
            for (FigmaComponent component : pageComponents) {
                try {
                    // Generate component JSON from database entity
                    String componentJson = generateComponentJsonFromEntity(component);
                    
                    // Sanitize component name
                    String componentName = FileNameSanitizer.sanitize(component.getFigmaNodeName());
                    if (componentName.isEmpty()) {
                        componentName = "component_" + component.getId();
                    }
                    
                    // Build MinIO path
                    String minioPath = buildComponentPath(projectName, pageName, componentName);
                    
                    // Upload to MinIO
                    minIOService.uploadDesign(minioPath, componentJson);
                    storedComponentPaths.add(minioPath);
                    
                } catch (Exception e) {
                    log.error("Failed to generate component from entity ID: {}", 
                        component.getId(), e);
                    // Continue with other components
                }
            }
            
            log.info("Successfully generated and stored {} components for page ID: {}", 
                storedComponentPaths.size(), pageId);
            
        } catch (Exception e) {
            log.error("Failed to generate components from page ID: {}", pageId, e);
            throw new RuntimeException("Failed to generate components: " + e.getMessage());
        }
        
        return storedComponentPaths;
    }

    /**
     * Generate component JSON from FigmaComponent database entity
     * @param component FigmaComponent entity
     * @return Component JSON string
     */
    private String generateComponentJsonFromEntity(FigmaComponent component) throws Exception {
        ObjectNode componentJson = objectMapper.createObjectNode();
        
        componentJson.put("componentId", component.getId());
        componentJson.put("nodeId", component.getFigmaNodeId());
        componentJson.put("name", component.getFigmaNodeName());
        componentJson.put("type", component.getFigmaNodeType());
        componentJson.put("pageId", component.getPage().getId());
        
        // Position
        ObjectNode position = objectMapper.createObjectNode();
        position.put("x", component.getPositionX() != null ? component.getPositionX() : 0.0);
        position.put("y", component.getPositionY() != null ? component.getPositionY() : 0.0);
        componentJson.set("position", position);
        
        // Size
        ObjectNode size = objectMapper.createObjectNode();
        size.put("width", component.getNodeWidth() != null ? component.getNodeWidth() : 0.0);
        size.put("height", component.getNodeHeight() != null ? component.getNodeHeight() : 0.0);
        componentJson.set("size", size);
        
        // Additional metadata
        componentJson.put("sourceFileKey", component.getSourceFileKey());
        componentJson.put("sourceFileName", component.getSourceFileName());
        componentJson.put("createdAt", System.currentTimeMillis());
        
        return objectMapper.writeValueAsString(componentJson);
    }

    /**
     * Parse page JSON string and extract all components
     * @param pageJsonString Raw page JSON from Figma export
     * @return List of component JSON strings
     */
    public List<String> parsePageAndGenerateComponents(String pageJsonString) {
        List<String> componentJsonList = new ArrayList<>();
        
        try {
            JsonNode pageNode = objectMapper.readTree(pageJsonString);
            
            // Extract page metadata
            String pageName = pageNode.has("pageName") ? pageNode.get("pageName").asText() : "unknown";
            String pageId = pageNode.has("pageId") ? pageNode.get("pageId").asText() : "";
            int componentCount = pageNode.has("componentCount") ? pageNode.get("componentCount").asInt() : 0;
            
            log.info("Parsing page: {} with {} components", pageName, componentCount);
            
            // Extract components array
            if (!pageNode.has("components") || !pageNode.get("components").isArray()) {
                log.warn("No components array found in page JSON");
                return componentJsonList;
            }
            
            for (JsonNode componentNode : pageNode.get("components")) {
                try {
                    String componentJson = generateComponentJsonFromNode(componentNode, pageId);
                    componentJsonList.add(componentJson);
                } catch (Exception e) {
                    String componentId = componentNode.has("id") ? componentNode.get("id").asText() : "unknown";
                    log.error("Failed to parse component: {}", componentId, e);
                }
            }
            
            log.info("Successfully extracted {} components from page", componentJsonList.size());
            
        } catch (Exception e) {
            log.error("Failed to parse page JSON", e);
            throw new RuntimeException("Failed to parse page JSON: " + e.getMessage());
        }
        
        return componentJsonList;
    }

    /**
     * Generate component JSON from a single component node
     * @param componentNode Component data from page JSON
     * @param pageId Figma page ID for reference
     * @return Component JSON string
     */
    private String generateComponentJsonFromNode(JsonNode componentNode, String pageId) throws Exception {
        ObjectNode componentJson = objectMapper.createObjectNode();
        
        // Extract component properties
        String componentId = componentNode.has("id") ? componentNode.get("id").asText() : "";
        String componentName = componentNode.has("name") ? componentNode.get("name").asText() : "";
        String componentType = componentNode.has("type") ? componentNode.get("type").asText() : "";
        
        // Build component JSON structure
        componentJson.put("componentId", componentId);
        componentJson.put("nodeId", componentId);  // In Figma export, id is the nodeId
        componentJson.put("name", componentName);
        componentJson.put("type", componentType);
        componentJson.put("pageId", pageId);
        
        // Extract position
        if (componentNode.has("x") || componentNode.has("y")) {
            ObjectNode position = objectMapper.createObjectNode();
            position.put("x", componentNode.has("x") ? componentNode.get("x").asDouble() : 0.0);
            position.put("y", componentNode.has("y") ? componentNode.get("y").asDouble() : 0.0);
            componentJson.set("position", position);
        }
        
        // Extract size
        if (componentNode.has("width") || componentNode.has("height")) {
            ObjectNode size = objectMapper.createObjectNode();
            size.put("width", componentNode.has("width") ? componentNode.get("width").asDouble() : 0.0);
            size.put("height", componentNode.has("height") ? componentNode.get("height").asDouble() : 0.0);
            componentJson.set("size", size);
        }
        
        // Add optional text content
        if (componentNode.has("text") && !componentNode.get("text").isNull()) {
            componentJson.put("text", componentNode.get("text").asText());
        }
        
        // Add creation timestamp
        componentJson.put("createdAt", System.currentTimeMillis());
        
        // Copy any additional properties that might be useful
        if (componentNode.has("fillColor")) {
            componentJson.put("fillColor", componentNode.get("fillColor").asText());
        }
        
        if (componentNode.has("strokeColor")) {
            componentJson.put("strokeColor", componentNode.get("strokeColor").asText());
        }
        
        // Keep raw component data for reference
        componentJson.set("rawData", componentNode);
        
        return objectMapper.writeValueAsString(componentJson);
    }

    /**
     * Store all components from page JSON to MinIO in hierarchical structure
     * @param projectName Sanitized project name
     * @param pageName Sanitized page name
     * @param pageJsonString Page JSON content
     * @return List of MinIO paths for stored components
     */
    public List<String> storeComponentsFromPageJson(String projectName, String pageName, String pageJsonString) {
        List<String> storedPaths = new ArrayList<>();
        
        try {
            // Parse components from page JSON
            List<String> componentJsonList = parsePageAndGenerateComponents(pageJsonString);
            
            log.info("Storing {} components for page: {}", componentJsonList.size(), pageName);
            
            int index = 0;
            for (String componentJson : componentJsonList) {
                try {
                    // Parse component to get name
                    JsonNode componentNode = objectMapper.readTree(componentJson);
                    String componentName = componentNode.has("name") ? 
                            componentNode.get("name").asText() : 
                            "component_" + index;
                    
                    // Build MinIO path
                    String minioPath = buildComponentPath(projectName, pageName, componentName);
                    
                    // Upload to MinIO
                    minIOService.uploadDesign(minioPath, componentJson);
                    storedPaths.add(minioPath);
                    
                    index++;
                } catch (Exception e) {
                    log.error("Failed to store component from page JSON, index: {}", index, e);
                }
            }
            
            log.info("Successfully stored {} components to MinIO", storedPaths.size());
            
        } catch (Exception e) {
            log.error("Failed to store components from page JSON for page: {}", pageName, e);
            throw new RuntimeException("Failed to store components: " + e.getMessage());
        }
        
        return storedPaths;
    }

    /**
     * Build hierarchical MinIO path for component
     * Path: {projectName}/pages/{pageName}/figma-components/{componentName}.json
     * @param projectName Sanitized project name
     * @param pageName Sanitized page name
     * @param componentName Sanitized component name
     * @return Full MinIO object path
     */
    private String buildComponentPath(String projectName, String pageName, String componentName) {
        String sanitizedComponentName = componentName
                .toLowerCase()
                .replaceAll("[^a-z0-9\\-_]", "")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        
        if (sanitizedComponentName.isEmpty()) {
            sanitizedComponentName = "component";
        }
        
        return String.format("%s/pages/%s/figma-components/%s.json", projectName, pageName, sanitizedComponentName);
    }

    /**
     * Extract page metadata from page JSON
     * @param pageJsonString Page JSON content
     * @return PageMetadata object
     */
    public PageMetadata extractPageMetadata(String pageJsonString) {
        try {
            JsonNode pageNode = objectMapper.readTree(pageJsonString);
            
            return PageMetadata.builder()
                    .pageId(pageNode.has("pageId") ? pageNode.get("pageId").asText() : "")
                    .pageName(pageNode.has("pageName") ? pageNode.get("pageName").asText() : "")
                    .componentCount(pageNode.has("componentCount") ? pageNode.get("componentCount").asInt() : 0)
                    .createdAt(pageNode.has("createdAt") ? pageNode.get("createdAt").asLong() : System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("Failed to extract page metadata", e);
            throw new RuntimeException("Failed to extract metadata: " + e.getMessage());
        }
    }

    /**
     * Page metadata DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class PageMetadata {
        private String pageId;
        private String pageName;
        private Integer componentCount;
        private Long createdAt;
    }
}
