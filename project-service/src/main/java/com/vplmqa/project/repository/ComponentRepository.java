package com.vplmqa.project.repository;

import com.vplmqa.project.entity.Component;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for components.
 */
public interface ComponentRepository extends JpaRepository<Component, UUID> {

    /**
     * Finds components by page.
     *
     * @param pageId page id
     * @return list of components
     */
    List<Component> findByPageId(UUID pageId);

    /**
     * Finds a component by canonical name and project id.
     *
     * @param canonicalName canonical name
     * @param projectId project id
     * @return component if present
     */
    Optional<Component> findByCanonicalNameAndPage_Project_Id(String canonicalName, UUID projectId);

    Optional<Component> findByCanonicalNameAndPage_Id(String canonicalName, UUID pageId);

    /**
     * Deletes all components that belong to a page.
     *
     * @param pageId page id
     */
    void deleteByPageId(UUID pageId);

    /**
     * Finds all components that belong to a project.
     *
     * @param projectId project id
     * @return component list
     */
    List<Component> findByPage_Project_Id(UUID projectId);
}
