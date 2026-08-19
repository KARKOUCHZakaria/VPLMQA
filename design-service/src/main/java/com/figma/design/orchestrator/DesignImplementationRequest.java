package com.figma.design.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for design implementation process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignImplementationRequest {
    private String projectName;
    private String projectUrl;
    private Integer figmaProjectKey;
    private String figmaProjectName;
    private String figmaApiKey;
}
