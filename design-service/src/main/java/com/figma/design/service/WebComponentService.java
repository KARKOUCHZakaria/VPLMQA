package com.figma.design.service;

import com.figma.design.model.WebComponent;
import java.util.List;

public interface WebComponentService {

    List<WebComponent> findAll();

    WebComponent findById(Long id);

    WebComponent create(WebComponent webComponent, Long pageId);

    WebComponent update(Long id, WebComponent webComponent, Long pageId);

    void delete(Long id);
}