package com.figma.design.service;

import com.figma.design.model.Page;
import com.figma.design.repository.PageRepository;
import com.figma.design.util.FileNameSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for migrating page fileLink paths from old flat structure to new hierarchical structure
 * Old: designs/page_name.json
 * New: {projectName}/pages/{pageName}/page.json
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PagePathMigrationService {

    private final PageRepository pageRepository;
    private final ComponentStorageService componentStorageService;

    /**
     * Migrate a single page's fileLink to hierarchical structure
     * @param pageId Page entity ID
     * @return Updated page entity with new fileLink
     */
    public Page migratePagePath(Long pageId) {
        try {
            Page page = pageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));
            
            String oldPath = page.getFileLink();
            
            if (oldPath != null && oldPath.startsWith("designs/")) {
                log.info("Migrating page fileLink for page ID: {} from {} to hierarchical structure", 
                    pageId, oldPath);
                
                // Build new hierarchical path
                String projectName = FileNameSanitizer.sanitize(page.getProject().getName());
                String pageName = FileNameSanitizer.sanitize(page.getName());
                String newPath = componentStorageService.buildPagePath(projectName, pageName);
                
                // Update page with new path
                page.setFileLink(newPath);
                Page updatedPage = pageRepository.saveAndFlush(page);
                
                log.info("Successfully migrated page fileLink from {} to {}", oldPath, newPath);
                return updatedPage;
            } else {
                log.info("Page ID: {} already has hierarchical path or no fileLink", pageId);
                return page;
            }
            
        } catch (Exception e) {
            log.error("Failed to migrate page path for page ID: {}", pageId, e);
            throw new RuntimeException("Failed to migrate page path: " + e.getMessage());
        }
    }

    /**
     * Migrate all pages with old path structure (any format) to hierarchical
     * Handles: designs/*, plain names, or any other old format
     * @return Number of pages migrated
     */
    public int migrateAllPagePathsComprehensive() {
        try {
            List<Page> allPages = pageRepository.findAll();
            int migratedCount = 0;
            
            for (Page page : allPages) {
                try {
                    String fileLink = page.getFileLink();
                    
                    // Check if page needs migration (any non-hierarchical format)
                    // Hierarchical format: {projectName}/pages/{pageName}/page.json
                    boolean needsMigration = false;
                    
                    if (fileLink == null || fileLink.isEmpty()) {
                        needsMigration = true;
                    } else if (!fileLink.contains("/pages/")) {
                        // Old format doesn't contain /pages/ structure
                        needsMigration = true;
                    }
                    
                    if (needsMigration) {
                        log.info("Migrating page: {} (ID: {}) from: {}", 
                            page.getName(), page.getId(), fileLink);
                        
                        // Build new hierarchical path
                        String projectName = FileNameSanitizer.sanitize(page.getProject().getName());
                        String pageName = FileNameSanitizer.sanitize(page.getName());
                        String newPath = componentStorageService.buildPagePath(projectName, pageName);
                        
                        // Update page
                        page.setFileLink(newPath);
                        pageRepository.saveAndFlush(page);
                        migratedCount++;
                        
                        log.info("Migrated page {} to: {}", page.getName(), newPath);
                    } else {
                        log.debug("Page {} already has hierarchical structure: {}", 
                            page.getName(), fileLink);
                    }
                } catch (Exception e) {
                    log.error("Failed to migrate page: {} (ID: {})", page.getName(), page.getId(), e);
                    // Continue with other pages
                }
            }
            
            log.info("Comprehensive migration complete. Total pages migrated: {}", migratedCount);
            return migratedCount;
            
        } catch (Exception e) {
            log.error("Failed to migrate page paths", e);
            throw new RuntimeException("Failed to migrate page paths: " + e.getMessage());
        }
    }

    /**
     * Get summary of pages that need migration (any non-hierarchical format)
     * @return Number of pages with old format
     */
    public int countPagesToMigrateComprehensive() {
        try {
            List<Page> allPages = pageRepository.findAll();
            long countOldPaths = allPages.stream()
                    .filter(p -> {
                        String fileLink = p.getFileLink();
                        return fileLink == null || fileLink.isEmpty() || !fileLink.contains("/pages/");
                    })
                    .count();
            
            log.info("Pages needing hierarchical migration: {}/{}", countOldPaths, allPages.size());
            return (int) countOldPaths;
            
        } catch (Exception e) {
            log.error("Failed to count pages to migrate", e);
            throw new RuntimeException("Failed to count pages: " + e.getMessage());
        }
    }


}
