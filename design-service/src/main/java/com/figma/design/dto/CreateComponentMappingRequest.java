package com.figma.design.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for mapping a web component to a Figma component
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateComponentMappingRequest {

    /**
     * ID of the web component to map
     */
    private Long webComponentId;

    /**
     * ID of the Figma component to map to
     */
    private Long figmaComponentId;

    /**
     * Alternative: Figma node ID (used if figmaComponentId not available)
     */
    private String figmaNodeId;

    /**
     * Test identifier for this component (e.g., "btn_login_submit")
     * Convention: snake_case, descriptive names
     * Pattern: {element_type}_{parent_context}_{specific_action}
     */
    private String testIdentifier;

    /**
     * Functional role/purpose (e.g., "Login Button", "Navigation Header")
     */
    private String functionalRole;

    /**
     * Semantic description of what this component does
     */
    private String componentDescription;

    /**
     * Expected Figma component type (e.g., "Button", "Card", "Form")
     */
    private String figmaComponentType;

    /**
     * Optional notes about the mapping
     */
    private String mappingNotes;

    /**
     * Whether to perform immediate comparison after mapping
     */
    private boolean performComparison;
}
