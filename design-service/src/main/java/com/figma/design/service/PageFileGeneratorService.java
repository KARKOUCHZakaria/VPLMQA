package com.figma.design.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.figma.FigmaDesignImportResponse;
import com.figma.design.exception.FigmaIntegrationException;
import com.figma.design.model.Page;
import com.figma.design.model.Project;
import com.figma.design.repository.PageRepository;
import com.figma.design.repository.ProjectRepository;
import com.figma.design.service.DesignFileParserService.ParsedPageData;
import com.figma.design.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating page files from parsed design and creating Page entities
 * Implements hierarchical MinIO storage: /{projectName}/design.json and /{projectName}/pages/{pageName}/
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PageFileGeneratorService {

    private final DesignFileParserService designFileParserService;
    private final MinIOService minIOService;
    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;
    private final ComponentStorageService componentStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Generate page files from design and create Page entities
     * Creates hierarchical structure: /{projectName}/design.json and /{projectName}/pages/{pageName}/
     * @param projectId Project ID
     * @param designJson Parsed design response
     * @return List of created Page entities
     */
    public List<Page> generatePagesFromDesign(Long projectId, FigmaDesignImportResponse designJson) {
        // Fetch project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        // Sanitize project name for MinIO folder structure
        String projectNameSanitized = FileNameSanitizer.sanitize(project.getName());

        // Parse design to extract pages
        List<ParsedPageData> parsedPages = designFileParserService.parseDesignPages(designJson);
        List<Page> createdPages = new ArrayList<>();

        // First, store design file at root: /{projectName}/design.json
        try {
            String designJsonContent = objectMapper.writeValueAsString(designJson);
            String designPath = componentStorageService.buildDesignPath(projectNameSanitized);
            minIOService.uploadDesign(designPath, designJsonContent);
            log.info("Design root file stored at: {}", designPath);
        } catch (Exception e) {
            log.warn("Failed to store design root file, continuing with pages", e);
        }

        for (ParsedPageData pageData : parsedPages) {
            try {
                // Sanitize page name for folder structure
                String pageNameSanitized = FileNameSanitizer.sanitize(pageData.getPageName());

                // Generate page file content
                String pageFileContent = designFileParserService.generatePageFileContent(pageData);

                // Build hierarchical MinIO path for page: /{projectName}/pages/{pageName}/page.json
                String pageFilePath = componentStorageService.buildPagePath(projectNameSanitized, pageNameSanitized);

                // Upload page file to MinIO
                minIOService.uploadDesign(pageFilePath, pageFileContent);
                log.info("Page file uploaded to hierarchical MinIO path: {}", pageFilePath);

                // Create Page entity (save first to get ID for component storage)
                Page page = new Page();
                
                // Ensure page name doesn't exceed 500 chars (DB limit)
                String safeName = pageData.getPageName().length() > 500 
                    ? pageData.getPageName().substring(0, 500) 
                    : pageData.getPageName();
                page.setName(safeName);
                
                page.setProject(project);
                
                // Generate safe URL with length checking (max 1000 chars for DB)
                String pageUrl = generateSafePageUrl(project.getName(), pageData.getPageName());
                page.setUrl(pageUrl);
                
                page.setFigmaPageId(pageData.getPageId());
                page.setFileLink(pageFilePath);  // Link to /{projectName}/pages/{pageName}/page.json
                page.setComponentCount(pageData.getComponentCount());
                page.setImportedAt(LocalDateTime.now());

                // Save page entity to get ID
                Page savedPage = pageRepository.save(page);
                createdPages.add(savedPage);

                // Now store components for this page: /{projectName}/pages/{pageName}/figma-components/{componentName}.json
                List<String> componentPaths = componentStorageService.storeComponentsForPage(
                        projectNameSanitized,
                        pageNameSanitized,
                        savedPage.getId()
                );
                log.info("Stored {} components for page: {} in structure: /{}/pages/{}/figma-components/", 
                    componentPaths.size(), pageData.getPageName(), projectNameSanitized, pageNameSanitized);

                log.info("Created page: {} with {} components, linked to: {}", 
                    pageData.getPageName(), pageData.getComponentCount(), pageFilePath);

            } catch (Exception e) {
                log.error("Failed to generate page file for: {}", pageData.getPageName(), e);
                throw new FigmaIntegrationException("Failed to generate page file: " + e.getMessage());
            }
        }

        log.info("Generated hierarchical structure for {} pages in project: {}", createdPages.size(), project.getName());
        return createdPages;
    }

    /**
     * Generate a safe page URL that respects database column length constraints (1000 chars)
     * @param projectName Project name
     * @param pageFileName Sanitized page file name
     * @return Safe URL for page
     */
    private String generateSafePageUrl(String projectName, String pageFileName) {
        String baseUrl = "/pages/" + projectName.toLowerCase() + "/";
        String fullUrl = baseUrl + pageFileName;

        // Ensure URL doesn't exceed 1000 characters (DB constraint)
        int maxLength = 1000;
        if (fullUrl.length() > maxLength) {
            // If too long, truncate the pageFileName part while preserving path structure
            int maxPageFileName = maxLength - baseUrl.length();
            String truncatedFileName = pageFileName.length() > maxPageFileName 
                ? pageFileName.substring(0, maxPageFileName) 
                : pageFileName;
            fullUrl = baseUrl + truncatedFileName;
            log.warn("Page URL truncated to {} chars: {}", maxLength, fullUrl);
        }

        return fullUrl;
    }

    /**
     * Result of page file generation
     */
    @lombok.Data
    @lombok.Builder
    public static class PageGenerationResult {
        private Long projectId;
        private Integer totalPagesGenerated;
        private Integer totalComponents;
        private List<PageInfo> pages;

        @lombok.Data
        @lombok.Builder
        public static class PageInfo {
            private Long pageId;
            private String pageName;
            private String fileLink;
            private String url;
            private Integer componentCount;
        }
    }
}
