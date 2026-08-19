package com.figma.design.service;

import com.figma.design.exception.FigmaIntegrationException;
import com.figma.design.model.FigmaComponent;
import com.figma.design.repository.FigmaComponentRepository;
import com.figma.design.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for storing components in hierarchical MinIO structure
 * Structure: 
 * {projectName}/
 *   design.json
 *   pages/
 *     {pageName}/
 *       page.json
 *       figma-components/
 *         {componentName}.json
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ComponentStorageService {

    private final MinIOService minIOService;
    private final ComponentFileGenerator componentFileGenerator;
    private final FigmaComponentRepository figmaComponentRepository;

    /**
     * Store all components for a specific page in hierarchical MinIO structure
     * @param projectName Project name (sanitized)
     * @param pageName Page name (sanitized)
     * @param pageId Page entity ID
     * @return List of MinIO paths for stored components
     */
    public List<String> storeComponentsForPage(String projectName, String pageName, Long pageId) {
        List<String> storedComponentPaths = new ArrayList<>();
        
        try {
            // Fetch all components for this page
            List<FigmaComponent> pageComponents = figmaComponentRepository.findByPage_Id(pageId);
            
            if (pageComponents.isEmpty()) {
                log.info("No components found for page: {}", pageId);
                return storedComponentPaths;
            }

            log.info("Storing {} components for page: {}", pageComponents.size(), pageName);

            // Create components folder for each component
            for (FigmaComponent component : pageComponents) {
                try {
                    String componentPath = storeComponent(projectName, pageName, component);
                    storedComponentPaths.add(componentPath);
                } catch (Exception e) {
                    log.error("Failed to store component: {} for page: {}", 
                            component.getFigmaNodeId(), pageName, e);
                    // Continue with other components instead of failing the whole batch
                }
            }

            log.info("Successfully stored {} components for page: {}", 
                    storedComponentPaths.size(), pageName);
            
        } catch (Exception e) {
            log.error("Failed to store components for page: {} in project: {}", 
                    pageName, projectName, e);
            throw new FigmaIntegrationException("Failed to store components: " + e.getMessage());
        }

        return storedComponentPaths;
    }

    /**
     * Store a single component as JSON in MinIO
     * Path: /{projectName}/pages/{pageName}/figma-components/{componentName}.json
     * 
     * @param projectName Sanitized project name
     * @param pageName Sanitized page name
     * @param component FigmaComponent to store
     * @return MinIO object path
     */
    private String storeComponent(String projectName, String pageName, FigmaComponent component) {
        try {
            // Generate component JSON content
            String componentJson = componentFileGenerator.generateComponentJson(component);

            // Create safe component file name from node name
            String componentFileName = FileNameSanitizer.sanitize(component.getFigmaNodeName());
            if (componentFileName.isEmpty()) {
                componentFileName = "component_" + component.getFigmaNodeId();
            }

            // Build hierarchical object path
            // Format: /{projectName}/pages/{pageName}/figma-components/{componentName}.json
            String objectPath = buildComponentPath(projectName, pageName, componentFileName);

            // Upload to MinIO
            String minioPath = minIOService.uploadDesign(objectPath, componentJson);
            
            log.info("Component stored at: {} for page: {}", minioPath, pageName);
            return minioPath;

        } catch (Exception e) {
            log.error("Failed to store individual component: {}", component.getFigmaNodeId(), e);
            throw new RuntimeException("Failed to store component", e);
        }
    }

    /**
     * Build hierarchical MinIO path for component
     * Path: /{projectName}/pages/{pageName}/figma-components/{componentFileName}.json
     * 
     * @param projectName Sanitized project name
     * @param pageName Sanitized page name
     * @param componentFileName Sanitized component file name
     * @return Full MinIO object path
     */
    public String buildComponentPath(String projectName, String pageName, String componentFileName) {
        return FileNameSanitizer.generateObjectName(
                projectName,
                "pages",
                pageName,
                "figma-components",
                componentFileName
        ) + ".json";
    }

    /**
     * Build hierarchical MinIO path for a page file
     * Path: /{projectName}/pages/{pageName}/page.json
     * 
     * @param projectName Sanitized project name
     * @param pageName Sanitized page name
     * @return Full MinIO object path for page
     */
    public String buildPagePath(String projectName, String pageName) {
        return FileNameSanitizer.generateObjectName(
                projectName,
                "pages",
                pageName
        ) + ".json";
    }

    /**
     * Build hierarchical MinIO path for design file
     * Path: /{projectName}/design.json
     * 
     * @param projectName Sanitized project name
     * @return Full MinIO object path for design
     */
    public String buildDesignPath(String projectName) {
        return FileNameSanitizer.generateObjectName(
                projectName
        ) + "/design.json";
    }
}
