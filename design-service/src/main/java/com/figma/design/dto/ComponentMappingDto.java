package com.figma.design.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for component mapping between Figma and Web implementations
 * 
 * Purpose:
 * - Establish and track relationships between Figma design components and Web implementation components
 * - Enable E2E tests to identify components by test identifiers
 * - Provide traceability from design to implementation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentMappingDto {

    // ==================== Web Component Info ====================
    private Long webComponentId;
    private String webComponentName;
    private String htmlTag;
    private String htmlId;
    private String htmlClass;
    private String cssSelector;
    private String functionalRole;
    private String testIdentifier;
    private String componentDescription;

    // ==================== Figma Component Info ====================
    private Long figmaComponentId;
    private String figmaNodeId;
    private String figmaNodeName;
    private String figmaNodeType;
    private String figmaComponentType;
    private Map<String, Object> figmaMetadata; // Position, size, etc.

    // ==================== Mapping Status ====================
    private String mappingStatus; // UNMAPPED, MAPPED, PARTIALLY_MAPPED, MISMATCH
    private LocalDateTime mappedAt;
    private LocalDateTime lastComparisonAt;
    private String mappingNotes;

    // ==================== Comparison Results ====================
    private ComparisonResult comparisonResult;

    /**
     * Detailed comparison between Figma design and Web implementation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComparisonResult {
        private boolean structureMatch; // HTML structure matches Figma design
        private boolean styleMatch; // CSS styles match Figma design
        private boolean contentMatch; // Text content matches Figma design
        private List<Difference> differences; // List of found differences
        private double matchPercentage; // 0-100% match
        private String recommendation; // What to fix
    }

    /**
     * Single difference between Figma and Web implementation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Difference {
        private String aspect; // "structure", "style", "content", "layout"
        private String figmaValue; // Value from Figma design
        private String webValue; // Value from Web implementation
        private String severity; // "critical", "warning", "info"
        private String description; // Human-readable description
    }

    // ==================== For E2E Test Identification ====================
    /**
     * Full identifier chain priority: testIdentifier > functionalRole > componentName
     */
    public String getFullIdentifier() {
        if (testIdentifier != null && !testIdentifier.isEmpty()) {
            return testIdentifier;
        }
        if (functionalRole != null && !functionalRole.isEmpty()) {
            return functionalRole;
        }
        return webComponentName;
    }

    /**
     * Get search-friendly string for finding this component
     * Format: "test_id | functional_role | name | html_id"
     */
    public String getSearchKey() {
        StringBuilder sb = new StringBuilder();
        if (testIdentifier != null) sb.append(testIdentifier).append(" | ");
        if (functionalRole != null) sb.append(functionalRole).append(" | ");
        sb.append(webComponentName).append(" | ");
        if (htmlId != null) sb.append(htmlId);
        return sb.toString();
    }
}
