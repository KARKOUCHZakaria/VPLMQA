package com.figma.design.dto.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for page creation parameters passed to the factory
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageCreationDto {
    private String name;
    private String projectName;
    private String figmaPageId;
    private String figmaUrl;
    private String url;
    private String tag;
}
