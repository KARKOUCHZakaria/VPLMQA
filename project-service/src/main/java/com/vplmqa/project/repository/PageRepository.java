package com.vplmqa.project.repository;

import com.vplmqa.project.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for pages.
 */
public interface PageRepository extends JpaRepository<Page, UUID> {

    /**
     * Finds pages by project id.
     *
     * @param projectId project id
     * @return list of pages
     */
    List<Page> findByProjectId(UUID projectId);
}
