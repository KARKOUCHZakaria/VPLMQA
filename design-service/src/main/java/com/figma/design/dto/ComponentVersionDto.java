package com.figma.design.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing a component version/snapshot at a point in time
 * 
 * Used for tracking component evolution:
 * Version 1: Initial Figma Design
 * Version 2: Figma Updates
 * Version 3: Web Implementation
 * Version 4: Web Updates/Fixes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentVersionDto {

    // ==================== Version Identity ====================
    private Long versionId;
    private Long componentId;              // Which component is this version of
    private Integer versionNumber;         // v1, v2, v3, etc.
    private String versionLabel;           // "Initial Design", "First Update", "Web Implementation"

    // ==================== Version Content ====================
    private String componentName;          // Component name in this version
    private String componentType;          // Type in this version
    private String functionalRole;         // Role description
    private String description;            // What changed from previous version
    
    // ==================== Component State ====================
    private String sourceType;             // FIGMA or WEB
    private Map<String, Object> properties; // Component properties/attributes in this version
    private String rawJson;                // Full JSON snapshot

    // ==================== Change Tracking ====================
    private LocalDateTime createdAt;       // When this version was created
    private String createdBy;              // Who created it (e.g., "figma-import", "web-dev-john", "auto-migration")
    private String changeReason;           // Why this version was created (e.g., "Design approved", "Bug fix", "Performance improvement")
    private String changeNotes;            // Additional notes about changes
    
    // ==================== Comparison ====================
    private Map<String, ComponentChange> changes; // What changed from previous version
    
    /**
     * Single field change from previous version
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentChange {
        private String fieldName;      // Which property changed (e.g., "width", "color", "cssSelector")
        private Object previousValue;  // Old value
        private Object currentValue;   // New value
        private String changeType;     // ADDED, MODIFIED, REMOVED
        private String reason;         // Why it changed
    }

    /**
     * Get a human-readable change summary
     */
    public String getChangeSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(versionLabel).append(" (v").append(versionNumber).append(")\n");
        summary.append("Created: ").append(createdAt).append("\n");
        summary.append("By: ").append(createdBy).append("\n");
        summary.append("Reason: ").append(changeReason).append("\n");
        
        if (changes != null && !changes.isEmpty()) {
            summary.append("Changes:\n");
            changes.forEach((field, change) -> {
                summary.append("  - ").append(field).append(": ")
                       .append(change.getPreviousValue()).append(" → ")
                       .append(change.getCurrentValue()).append("\n");
            });
        }
        
        return summary.toString();
    }
}
