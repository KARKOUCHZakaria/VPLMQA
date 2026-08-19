package com.figma.design.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.model.FigmaComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for generating JSON representations of components for MinIO storage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentFileGenerator {

    private final ObjectMapper objectMapper;

    /**
     * Generate JSON content for a Figma component
     * @param component FigmaComponent entity
     * @return JSON string representation
     */
    public String generateComponentJson(FigmaComponent component) {
        try {
            ComponentData componentData = ComponentData.builder()
                    .componentId(component.getId())
                    .nodeId(component.getFigmaNodeId())
                    .name(component.getFigmaNodeName())
                    .type(component.getType().toString())
                    .nodeType(component.getFigmaNodeType())
                    .position(PositionData.builder()
                            .x(component.getPositionX())
                            .y(component.getPositionY())
                            .build())
                    .size(SizeData.builder()
                            .width(component.getNodeWidth())
                            .height(component.getNodeHeight())
                            .build())
                    .sourceFileKey(component.getSourceFileKey())
                    .sourceFileName(component.getSourceFileName())
                    .rawJson(component.getRawJson())
                    .importedAt(component.getImportedAt() != null ? 
                            component.getImportedAt().toString() : null)
                    .createdAt(System.currentTimeMillis())
                    .build();
            
            return objectMapper.writeValueAsString(componentData);
        } catch (Exception e) {
            log.error("Failed to generate JSON for component: {}", component.getFigmaNodeId(), e);
            throw new RuntimeException("Failed to generate component JSON", e);
        }
    }

    /**
     * Data Transfer Object for component storage
     */
    @lombok.Data
    @lombok.Builder
    public static class ComponentData {
        private Long componentId;
        private String nodeId;
        private String name;
        private String type;
        private String nodeType;
        private PositionData position;
        private SizeData size;
        private String sourceFileKey;
        private String sourceFileName;
        private String rawJson;
        private String importedAt;
        private Long createdAt;
    }

    /**
     * Position information for component
     */
    @lombok.Data
    @lombok.Builder
    public static class PositionData {
        private Double x;
        private Double y;
    }

    /**
     * Size information for component
     */
    @lombok.Data
    @lombok.Builder
    public static class SizeData {
        private Double width;
        private Double height;
    }
}
