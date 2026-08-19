package com.vplmqa.project.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.project.dto.ComponentRequest;
import com.vplmqa.project.dto.ComponentResponse;
import com.vplmqa.project.dto.ComponentSemanticUpdateRequest;
import com.vplmqa.project.entity.Component;
import com.vplmqa.project.entity.Page;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.enumtype.ComponentStatusEnum;
import com.vplmqa.project.mapper.ProjectMapper;
import com.vplmqa.project.repository.ComponentRepository;
import com.vplmqa.project.repository.PageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for component operations.
 */
@Service
public class ComponentService {

    private final ComponentRepository componentRepository;
    private final PageRepository pageRepository;
    private final ProjectMapper projectMapper;

    /**
     * Creates the service.
     *
     * @param componentRepository component repository
     * @param pageRepository page repository
     * @param projectMapper mapper
     */
    public ComponentService(ComponentRepository componentRepository, PageRepository pageRepository, ProjectMapper projectMapper) {
        this.componentRepository = componentRepository;
        this.pageRepository = pageRepository;
        this.projectMapper = projectMapper;
    }

    /**
     * Upserts a component by canonical name and project id.
     *
     * @param request request payload
     * @return component response
     */
    @Transactional
    public ComponentResponse upsertComponent(ComponentRequest request) {
        Page page = findPage(request.pageId());
        Component component = componentRepository.findByCanonicalNameAndPage_Project_Id(request.canonicalName(), page.getProject().getId())
                .orElseGet(Component::new);
        component.setPage(page);
        component.setCanonicalName(request.canonicalName());
        component.setSemanticRole(request.semanticRole());
        component.setFunctionalMeaning(request.functionalMeaning());
        component.setHtmlId(request.htmlId());
        component.setFigmaNodeId(request.figmaNodeId());
        component.setSource(request.source());
        component.setStatus(request.status() == null ? ComponentStatusEnum.PENDING : request.status());
        component.setCssProperties(request.cssProperties());
        component.setBoundingBox(request.boundingBox());
        component.setScreenshot(request.screenshot());
        return projectMapper.toComponentResponse(componentRepository.save(component));
    }

    /**
     * Bulk upserts components.
     *
     * @param requests component requests
     * @return responses
     */
    @Transactional
    public List<ComponentResponse> bulkUpsert(List<ComponentRequest> requests) {
        List<ComponentResponse> responses = new java.util.ArrayList<>();
        for (ComponentRequest request : requests) {
            responses.add(upsertComponent(request));
        }
        return responses;
    }

    @Transactional
    public List<ComponentResponse> applySemanticUpdates(UUID projectId, List<ComponentSemanticUpdateRequest> requests) {
        List<ComponentResponse> responses = new java.util.ArrayList<>();
        for (ComponentSemanticUpdateRequest request : requests) {
            Component component = componentRepository.findById(request.componentId())
                    .orElseThrow(() -> new EntityNotFoundException("Component not found: " + request.componentId()));
            if (!component.getPage().getProject().getId().equals(projectId)) {
                throw new IllegalArgumentException("Component does not belong to project: " + request.componentId());
            }
            component.setCanonicalName(uniqueCanonicalName(component, request.canonicalName()));
            component.setSemanticRole(request.semanticRole());
            component.setFunctionalMeaning(request.functionalMeaning());
            component.setStatus(ComponentStatusEnum.VALIDATED);
            responses.add(projectMapper.toComponentResponse(componentRepository.save(component)));
        }
        return responses;
    }

    /**
     * Finds components by project id.
     *
     * @param projectId project id
     * @return component responses
     */
    @Transactional(readOnly = true)
    public List<ComponentResponse> findByProject(UUID projectId) {
        return componentRepository.findByPage_Project_Id(projectId).stream().map(projectMapper::toComponentResponse).toList();
    }

    /**
     * Finds components by page.
     *
     * @param pageId page id
     * @return responses
     */
    @Transactional(readOnly = true)
    public List<ComponentResponse> findByPage(UUID pageId) {
        return componentRepository.findByPageId(pageId).stream().map(projectMapper::toComponentResponse).toList();
    }

    /**
     * Finds a component by canonical name and project id.
     *
     * @param canonicalName canonical name
     * @param projectId project id
     * @return response
     */
    @Transactional(readOnly = true)
    public ComponentResponse findByCanonicalName(String canonicalName, UUID projectId) {
        return projectMapper.toComponentResponse(componentRepository.findByCanonicalNameAndPage_Project_Id(canonicalName, projectId)
                .orElseThrow(() -> new EntityNotFoundException("Component not found")));
    }

    private Page findPage(UUID id) {
        return pageRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Page not found"));
    }

    private String uniqueCanonicalName(Component component, String requestedName) {
        String baseName = requestedName == null || requestedName.isBlank() ? "component" : requestedName.trim();
        String candidate = baseName;
        int suffix = 2;
        while (true) {
            var existing = componentRepository.findByCanonicalNameAndPage_Id(candidate, component.getPage().getId());
            if (existing.isEmpty() || existing.get().getId().equals(component.getId())) {
                return candidate;
            }
            candidate = baseName + "_" + suffix++;
        }
    }
}
