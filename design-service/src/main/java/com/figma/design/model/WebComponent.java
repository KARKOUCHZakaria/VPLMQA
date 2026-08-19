package com.figma.design.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "web_components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebComponent extends Component {

    @Column(name = "component_name", nullable = false, length = 255)
    private String componentName;

    @Column(name = "html_tag", nullable = false, length = 100)
    private String htmlTag;

    @Column(name = "html_id", length = 255)
    private String htmlID;

    @Column(name = "html_class", length = 500)
    private String htmlClass;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes; // JSON string of HTML attributes

    @Column(name = "css_selector", nullable = false, columnDefinition = "TEXT")
    private String cssSelector;

    @Column(name = "xpath", columnDefinition = "TEXT")
    private String xpath;

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    @Column(name = "file_link", length = 500)
    private String fileLink; // Path to component JSON in MinIO

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson; // Full component JSON

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    // ==================== Component Mapping & Identification ====================
    
    /**
     * Reference to the Figma component this web component is mapped to.
     * Enables traceability from design to implementation.
     */
    @Column(name = "mapped_figma_component_id")
    private Long mappedFigmaComponentId;

    /**
     * Figma node ID this component corresponds to (for direct Figma API lookup)
     */
    @Column(name = "figma_node_id_reference")
    private String figmaNodeIdReference;

    /**
     * Functional role/description for easy identification (e.g., "Login Form", "Navigation Header", "Product Card")
     * Used for test identification and understanding component purpose
     */
    @Column(name = "functional_role", length = 255)
    private String functionalRole;

    /**
     * Unique test identifier for E2E tests (e.g., "btn_login_submit", "nav_header_main")
     * Convention: snake_case with descriptive names
     * Pattern: {element_type}_{parent_context}_{specific_action}
     */
    @Column(name = "test_identifier", length = 255, unique = false)
    private String testIdentifier;

    /**
     * Semantic description of what this component does (e.g., "Submits login form", "Displays navigation menu")
     * Helps understand component behavior during testing
     */
    @Column(name = "component_description", columnDefinition = "TEXT")
    private String componentDescription;

    /**
     * Expected role/type from Figma design (e.g., "Button", "Card", "Form")
     * Used for comparison/validation
     */
    @Column(name = "figma_component_type")
    private String figmaComponentType;

    /**
     * Mapping status: UNMAPPED, MAPPED, PARTIALLY_MAPPED, MISMATCH
     */
    @Column(name = "mapping_status", length = 50)
    private String mappingStatus; // UNMAPPED, MAPPED, PARTIALLY_MAPPED, MISMATCH

    /**
     * Timestamp when this component was mapped to a Figma component
     */
    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;

    /**
     * Last time the mapping was verified/compared
     */
    @Column(name = "last_comparison_at")
    private LocalDateTime lastComparisonAt;

    /**
     * Notes about mapping/comparison results (e.g., differences found)
     */
    @Column(name = "mapping_notes", columnDefinition = "TEXT")
    private String mappingNotes;

    @Override
    public void operation() {
        // Intentionally empty: concrete behavior can be added by the transformation pipeline.
    }

    /**
     * Returns full identifier chain: testIdentifier > functionalRole > componentName
     */
    public String getFullIdentifier() {
        if (testIdentifier != null && !testIdentifier.isEmpty()) {
            return testIdentifier;
        }
        if (functionalRole != null && !functionalRole.isEmpty()) {
            return functionalRole;
        }
        return componentName;
    }
}
