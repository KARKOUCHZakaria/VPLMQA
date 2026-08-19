package com.figma.design.dto.figma;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Request to generate page files from an imported design
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePagesFromDesignRequest {
    /**
     * Project ID to generate pages for
     */
    private Long projectId;
}
