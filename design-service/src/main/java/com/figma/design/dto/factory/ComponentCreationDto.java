package com.figma.design.dto.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for component creation parameters passed to the factory
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentCreationDto {
    private String name;
    private String nodeId;
    private String nodeType;
    private String componentPath;
    private Object properties;
}
