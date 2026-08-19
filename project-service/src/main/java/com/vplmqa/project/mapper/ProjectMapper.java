package com.vplmqa.project.mapper;

import com.vplmqa.project.dto.ComponentResponse;
import com.vplmqa.project.dto.PageResponse;
import com.vplmqa.project.dto.ProjectResponse;
import com.vplmqa.project.dto.ProjectSettingsResponse;
import com.vplmqa.project.entity.Component;
import com.vplmqa.project.entity.Page;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.entity.ProjectSettings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps project entities to response DTOs.
 */
@Mapper(componentModel = "spring")
public interface ProjectMapper {

    /**
     * Maps a project entity to a response.
     *
     * @param project project entity
     * @return project response
     */
    ProjectResponse toProjectResponse(Project project);

    /**
     * Maps a page entity to a response.
     *
     * @param page page entity
     * @return page response
     */
    @Mapping(target = "projectId", source = "project.id")
    PageResponse toPageResponse(Page page);

    /**
     * Maps a component entity to a response.
     *
     * @param component component entity
     * @return component response
     */
    @Mapping(target = "pageId", source = "page.id")
    ComponentResponse toComponentResponse(Component component);

    /**
     * Maps settings to a response.
     *
     * @param settings settings entity
     * @return settings response
     */
    @Mapping(target = "projectId", source = "project.id")
    ProjectSettingsResponse toSettingsResponse(ProjectSettings settings);
}
