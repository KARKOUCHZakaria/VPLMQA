package com.vplmqa.project.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.project.dto.PageRequest;
import com.vplmqa.project.dto.PageResponse;
import com.vplmqa.project.entity.Page;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.mapper.ProjectMapper;
import com.vplmqa.project.repository.ComponentRepository;
import com.vplmqa.project.repository.PageRepository;
import com.vplmqa.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for page operations.
 */
@Service
public class PageService {

    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;
    private final ComponentRepository componentRepository;
    private final ProjectMapper projectMapper;

    /**
     * Creates the service.
     *
     * @param pageRepository page repository
     * @param projectRepository project repository
     * @param projectMapper project mapper
     */
    public PageService(PageRepository pageRepository,
                       ProjectRepository projectRepository,
                       ComponentRepository componentRepository,
                       ProjectMapper projectMapper) {
        this.pageRepository = pageRepository;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
        this.projectMapper = projectMapper;
    }

    /**
     * Creates a page for a project.
     *
     * @param projectId project id
     * @param request request payload
     * @return page response
     */
    @Transactional
    public PageResponse createPage(UUID projectId, PageRequest request) {
        Project project = findProject(projectId);
        Page page = findExistingWebPage(projectId, request)
                .orElseGet(Page::new);
        page.setProject(project);
        page.setName(request.name());
        page.setUrl(request.url());
        page.setPath(request.path());
        page.setScanStatus("PENDING");
        return projectMapper.toPageResponse(pageRepository.save(page));
    }

    /**
     * Lists pages for a project.
     *
     * @param projectId project id
     * @return page responses
     */
    @Transactional(readOnly = true)
    public List<PageResponse> listPages(UUID projectId) {
        return pageRepository.findByProjectId(projectId).stream().map(projectMapper::toPageResponse).toList();
    }

    /**
     * Deletes a page that belongs to a project.
     *
     * @param projectId project id
     * @param pageId page id
     */
    @Transactional
    public PageResponse updatePage(UUID projectId, UUID pageId, PageRequest request) {
        Project project = findProject(projectId);
        Page page = findPage(pageId);
        if (!projectId.equals(page.getProject().getId())) {
            throw new EntityNotFoundException("Page not found for project");
        }
        if (page.getFigmaObjectPath() != null && !page.getFigmaObjectPath().isBlank()) {
            throw new IllegalStateException("Figma pages are managed from the imported design.");
        }
        page.setName(request.name().trim());
        page.setPath(normalizePath(request.path()));
        page.setUrl(joinUrl(project.getBaseUrl(), page.getPath()));
        page.setScanStatus("PENDING");
        return projectMapper.toPageResponse(pageRepository.save(page));
    }
    @Transactional
    public void deletePage(UUID projectId, UUID pageId) {
        Page page = findPage(pageId);
        if (page.getProject() == null || !projectId.equals(page.getProject().getId())) {
            throw new EntityNotFoundException("Page not found for project");
        }
        componentRepository.deleteByPageId(pageId);
        pageRepository.delete(page);
    }

    /**
     * Marks a page as scanned.
     *
     * @param pageId page id
     * @param scannedAt scan time
     */
    @Transactional
    public void markPageScanned(UUID pageId, Instant scannedAt) {
        Page page = findPage(pageId);
        page.setLastScannedAt(scannedAt);
        page.setScanStatus("SCANNED");
        pageRepository.save(page);
    }

    private Project findProject(UUID id) {
        return projectRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }

    private Page findPage(UUID id) {
        return pageRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Page not found"));
    }

    private java.util.Optional<Page> findExistingWebPage(UUID projectId, PageRequest request) {
        String requestedName = key(request.name());
        String requestedPath = key(request.path());
        String requestedUrl = normalizeUrl(request.url());
        return pageRepository.findByProjectId(projectId).stream()
                .filter(page -> page.getFigmaObjectPath() == null || page.getFigmaObjectPath().isBlank())
                .filter(page ->
                        (!requestedName.isBlank() && requestedName.equals(key(page.getName()))) ||
                        (!requestedPath.isBlank() && requestedPath.equals(key(page.getPath()))) ||
                        (!requestedUrl.isBlank() && requestedUrl.equals(normalizeUrl(page.getUrl()))))
                .findFirst();
    }

    private String key(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String normalizeUrl(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "").toLowerCase();
    }

    private String normalizePath(String value) {
        String path = value == null ? "/" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return base + normalizePath(path);
    }
}
