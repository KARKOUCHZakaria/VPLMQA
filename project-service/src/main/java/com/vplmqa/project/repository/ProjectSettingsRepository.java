package com.vplmqa.project.repository;

import com.vplmqa.project.entity.ProjectSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for project settings.
 */
public interface ProjectSettingsRepository extends JpaRepository<ProjectSettings, UUID> {

    /**
     * Finds settings by project id.
     *
     * @param projectId project id
     * @return settings if present
     */
    Optional<ProjectSettings> findByProjectId(UUID projectId);
}
