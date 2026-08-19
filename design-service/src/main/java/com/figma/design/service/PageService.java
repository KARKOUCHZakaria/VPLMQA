package com.figma.design.service;

import com.figma.design.model.Page;
import java.util.List;

public interface PageService {

    List<Page> findAll();

    Page findById(Long id);

    Page create(Page page, Long projectId);

    Page update(Long id, Page page, Long projectId);

    void delete(Long id);
}