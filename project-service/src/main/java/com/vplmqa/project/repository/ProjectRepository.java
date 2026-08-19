package com.vplmqa.project.repository;

import com.vplmqa.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for projects.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * Finds projects by creator.
     *
     * @param createdBy creator id
     * @return list of projects
     */
    List<Project> findByCreatedBy(UUID createdBy);

    /**
     * Finds projects by organization.
     *
     * @param organizationId organization id
     * @return list of projects
     */
    List<Project> findByOrganizationId(UUID organizationId);
}
