package com.figma.design.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.figma.FigmaDesignImportResponse;
import com.figma.design.util.FileNameSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing Figma design JSON and extracting page structure
 */
@Slf4j
@Service
public class DesignFileParserService {

    private final ObjectMapper objectMapper;

    public DesignFileParserService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse design JSON and extract page structure
     * @param designJson Design response object
     * @return List of parsed pages with their components
     */
    public List<ParsedPageData> parseDesignPages(FigmaDesignImportResponse designJson) {
        List<ParsedPageData> parsedPages = new ArrayList<>();

        for (FigmaDesignImportResponse.ImportedPage page : designJson.pages()) {
            ParsedPageData pageData = ParsedPageData.builder()
                    .pageId(page.id())
                    .pageName(page.name())
                    .componentCount(page.components().size())
                    .components(page.components())
                    .build();
            
            parsedPages.add(pageData);
            log.debug("Parsed page: {} with {} components", page.name(), page.components().size());
        }

        log.info("Extracted {} pages from design", parsedPages.size());
        return parsedPages;
    }

    /**
     * Generate JSON content for a single page file
     * @param pageData Parsed page data
     * @return JSON string for the page file
     */
    public String generatePageFileContent(ParsedPageData pageData) {
        try {
            PageFileContent pageFile = PageFileContent.builder()
                    .pageId(pageData.getPageId())
                    .pageName(pageData.getPageName())
                    .componentCount(pageData.getComponentCount())
                    .components(pageData.getComponents())
                    .createdAt(System.currentTimeMillis())
                    .build();

            return objectMapper.writeValueAsString(pageFile);
        } catch (Exception e) {
            log.error("Failed to generate page file content for page: {}", pageData.getPageName(), e);
            throw new RuntimeException("Failed to generate page file content", e);
        }
    }

    /**
     * Generate file name for a page
     * @param projectName Project name
     * @param pageName Page name from design
     * @return Safe file name
     */
    public String generatePageFileName(String projectName, String pageName) {
        // Use the robust sanitizer utility to generate safe file name
        return FileNameSanitizer.generateObjectName(projectName, pageName);
    }

    // ==================== DTOs ====================

    @lombok.Data
    @lombok.Builder
    public static class ParsedPageData {
        private String pageId;
        private String pageName;
        private Integer componentCount;
        private List<FigmaDesignImportResponse.ImportedComponent> components;
    }

    @lombok.Data
    @lombok.Builder
    public static class PageFileContent {
        private String pageId;
        private String pageName;
        private Integer componentCount;
        private List<FigmaDesignImportResponse.ImportedComponent> components;
        private Long createdAt;
    }
}
