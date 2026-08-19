package com.vplmqa.project.service;

import com.vplmqa.common.ApiException;
import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.project.dto.ProjectRequest;
import com.vplmqa.project.dto.ProjectResponse;
import com.vplmqa.project.entity.Project;
import com.vplmqa.project.enumtype.ProjectStatusEnum;
import com.vplmqa.project.event.ProjectCreatedEvent;
import com.vplmqa.project.mapper.ProjectMapper;
import com.vplmqa.project.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for project operations.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final KafkaTemplate<String, ProjectCreatedEvent> kafkaTemplate;

    /**
     * Creates the service.
     *
     * @param projectRepository project repository
     * @param projectMapper project mapper
     * @param kafkaTemplate kafka template
     */
    public ProjectService(ProjectRepository projectRepository,
                          ProjectMapper projectMapper,
                          KafkaTemplate<String, ProjectCreatedEvent> kafkaTemplate) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Creates a project.
     *
     * @param request request payload
     * @param createdBy creator id
     * @return project response
     */
    @Transactional
    public ProjectResponse createProject(ProjectRequest request, UUID createdBy) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setFigmaFileUrl(request.figmaFileUrl());
        project.setFigmaTokenEncrypted(request.figmaTokenEncrypted());
        project.setBaseUrl(request.baseUrl());
        project.setStatus(ProjectStatusEnum.ACTIVE);
        project.setCreatedBy(createdBy);
        project.setOrganizationId(request.organizationId());
        Project saved = projectRepository.save(project);
        kafkaTemplate.send("project.created", new ProjectCreatedEvent(saved.getId(), saved.getName(), saved.getOrganizationId(), saved.getCreatedBy()));
        return projectMapper.toProjectResponse(saved);
    }

    /**
     * Returns a project by id.
     *
     * @param id project id
     * @return project response
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID id) {
        return projectMapper.toProjectResponse(findProject(id));
    }

    /**
     * Updates a project.
     *
     * @param id project id
     * @param request request payload
     * @return project response
     */
    @Transactional
    public ProjectResponse updateProject(UUID id, ProjectRequest request) {
        Project project = findProject(id);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setFigmaFileUrl(request.figmaFileUrl());
        if (request.figmaTokenEncrypted() != null && !request.figmaTokenEncrypted().isBlank()) {
            project.setFigmaTokenEncrypted(request.figmaTokenEncrypted());
        }
        project.setBaseUrl(request.baseUrl());
        if (request.organizationId() != null) {
            project.setOrganizationId(request.organizationId());
        }
        return projectMapper.toProjectResponse(projectRepository.save(project));
    }

    /**
     * Archives a project.
     *
     * @param id project id
     */
    @Transactional
    public void archiveProject(UUID id) {
        Project project = findProject(id);
        project.setStatus(ProjectStatusEnum.ARCHIVED);
        projectRepository.save(project);
    }

    /**
     * Returns projects for a user.
     *
     * @param userId user id
     * @return projects
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByUser(UUID userId) {
        return projectRepository.findByCreatedBy(userId).stream().map(projectMapper::toProjectResponse).toList();
    }

    /**
     * Returns projects for an organization.
     *
     * @param organizationId organization id
     * @return projects
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByOrganization(UUID organizationId) {
        return projectRepository.findByOrganizationId(organizationId).stream().map(projectMapper::toProjectResponse).toList();
    }

    private Project findProject(UUID id) {
        return projectRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }
}
