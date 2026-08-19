package com.figma.design.service.impl;

import com.figma.design.exception.ResourceNotFoundException;
import com.figma.design.model.ComponentType;
import com.figma.design.model.Page;
import com.figma.design.model.WebComponent;
import com.figma.design.repository.PageRepository;
import com.figma.design.repository.WebComponentRepository;
import com.figma.design.service.WebComponentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WebComponentServiceImpl implements WebComponentService {

    private final WebComponentRepository webComponentRepository;
    private final PageRepository pageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WebComponent> findAll() {
        return webComponentRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public WebComponent findById(Long id) {
        return webComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebComponent", id));
    }

    @Override
    public WebComponent create(WebComponent webComponent, Long pageId) {
        webComponent.setType(ComponentType.WEB);
        webComponent.setPage(resolvePage(webComponent, pageId));
        return webComponentRepository.save(webComponent);
    }

    @Override
    public WebComponent update(Long id, WebComponent webComponent, Long pageId) {
        WebComponent existingComponent = findById(id);
        existingComponent.setType(ComponentType.WEB);
        existingComponent.setHtmlID(webComponent.getHtmlID());
        existingComponent.setPage(resolvePage(webComponent, pageId, existingComponent.getPage()));
        return webComponentRepository.save(existingComponent);
    }

    @Override
    public void delete(Long id) {
        WebComponent webComponent = findById(id);
        webComponentRepository.delete(webComponent);
    }

    private Page resolvePage(WebComponent webComponent, Long pageId) {
        return resolvePage(webComponent, pageId, null);
    }

    private Page resolvePage(WebComponent webComponent, Long pageId, Page fallbackPage) {
        Long resolvedPageId = pageId;
        if (resolvedPageId == null && webComponent != null && webComponent.getPage() != null) {
            resolvedPageId = webComponent.getPage().getId();
        }

        if (resolvedPageId == null) {
            if (fallbackPage != null) {
                return fallbackPage;
            }
            throw new IllegalArgumentException("pageId is required");
        }

        Long finalResolvedPageId = resolvedPageId;
        return pageRepository.findById(finalResolvedPageId)
            .orElseThrow(() -> new ResourceNotFoundException("Page", finalResolvedPageId));
    }
}