package com.figma.design.service;

import com.figma.design.dto.figma.FigmaDesignImportRequest;
import com.figma.design.dto.figma.FigmaDesignImportResponse;
import com.figma.design.dto.figma.ProjectDesignImportRequest;
import com.figma.design.model.FigmaComponent;
import com.figma.design.model.Page;
import java.util.List;

public interface FigmaComponentService {

    List<FigmaComponent> findAll();

    FigmaComponent findById(Long id);

    FigmaComponent create(FigmaComponent figmaComponent, Long pageId);

    FigmaComponent update(Long id, FigmaComponent figmaComponent, Long pageId);

    void delete(Long id);

    FigmaDesignImportResponse importFromFigma(FigmaDesignImportRequest request);

    /**
     * Import Figma design for a specific project, store in MinIO, and update project link
     * @param request ProjectDesignImportRequest with projectId and Figma file details
     * @return FigmaDesignImportResponse with design details
     */
    FigmaDesignImportResponse importDesignForProject(ProjectDesignImportRequest request);

    /**
     * Generate page files from imported design and create Page entities
     * @param projectId Project ID with imported design
     * @return List of created pages
     */
    List<Page> generatePagesFromDesign(Long projectId);

    /**
     * Extract all components from a specific Figma page
     */
    List<FigmaComponent> extractComponentsFromFigmaPage(String figmaPageId) throws Exception;
}