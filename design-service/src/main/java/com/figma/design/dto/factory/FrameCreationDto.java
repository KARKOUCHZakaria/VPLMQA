package com.figma.design.dto.factory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for frame creation parameters passed to the factory
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrameCreationDto {
    private String id;
    private Object properties;
    private Object children;
}
