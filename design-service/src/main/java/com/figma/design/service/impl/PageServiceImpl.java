package com.figma.design.service.impl;

import com.figma.design.exception.ResourceNotFoundException;
import com.figma.design.model.Page;
import com.figma.design.model.Project;
import com.figma.design.repository.PageRepository;
import com.figma.design.repository.ProjectRepository;
import com.figma.design.service.PageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PageServiceImpl implements PageService {

    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Page> findAll() {
        return pageRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page findById(Long id) {
        return pageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Page", id));
    }

    @Override
    public Page create(Page page, Long projectId) {
        page.setProject(resolveProject(page, projectId));
        return pageRepository.save(page);
    }

    @Override
    public Page update(Long id, Page page, Long projectId) {
        Page existingPage = findById(id);
        existingPage.setName(page.getName());
        existingPage.setUrl(page.getUrl());
        existingPage.setProject(resolveProject(page, projectId, existingPage.getProject()));
        return pageRepository.save(existingPage);
    }

    @Override
    public void delete(Long id) {
        Page page = findById(id);
        pageRepository.delete(page);
    }

    private Project resolveProject(Page page, Long projectId) {
        return resolveProject(page, projectId, null);
    }

    private Project resolveProject(Page page, Long projectId, Project fallbackProject) {
        Long resolvedProjectId = projectId;
        if (resolvedProjectId == null && page != null && page.getProject() != null) {
            resolvedProjectId = page.getProject().getId();
        }

        if (resolvedProjectId == null) {
            if (fallbackProject != null) {
                return fallbackProject;
            }
            throw new IllegalArgumentException("projectId is required");
        }

        Long finalResolvedProjectId = resolvedProjectId;
        return projectRepository.findById(finalResolvedProjectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", finalResolvedProjectId));
    }
}