package com.figma.design.service;

import com.figma.design.model.Page;
import com.figma.design.model.Project;
import com.figma.design.repository.PageRepository;
import com.figma.design.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Service for managing web pages
 * Handles creation and management of web page entities
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPageService {

    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;

    /**
     * Create a new web page for extraction
     * 
     * @param url Website URL to extract
     * @param tagName Display name/tag for the page
     * @param projectId Project ID this page belongs to
     * @return Created Page entity with ID
     */
    @Transactional
    public Page createWebPage(String url, String tagName, Long projectId) {
        try {
            // Validate project exists
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            log.info("Creating web page: {} for project: {} from URL: {}", tagName, project.getName(), url);

            // Create new page
            Page page = new Page();
            page.setName(tagName);
            page.setUrl(url);
            page.setProject(project);

            // Save and return
            Page savedPage = pageRepository.saveAndFlush(page);
            log.info("Web page created successfully with ID: {} and name: {}", savedPage.getId(), tagName);

            return savedPage;

        } catch (Exception e) {
            log.error("Failed to create web page: {} for project: {}", tagName, projectId, e);
            throw new RuntimeException("Web page creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get page by ID
     */
    public Page getPageById(Long pageId) {
        return pageRepository.findById(pageId)
                .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));
    }
}
