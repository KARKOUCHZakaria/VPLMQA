package com.figma.design.service.impl;

import com.figma.design.exception.ResourceNotFoundException;
import com.figma.design.model.Project;
import com.figma.design.repository.ProjectRepository;
import com.figma.design.service.ProjectService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Project findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    @Override
    public Project create(Project project) {
        // Ensure ID is null for new entities (ignore if client sends 0)
        if (project.getId() != null && project.getId() <= 0) {
            project.setId(null);
        }
        return projectRepository.save(project);
    }

    @Override
    public Project update(Long id, Project project) {
        Project existingProject = findById(id);
        existingProject.setName(project.getName());
        existingProject.setUrl(project.getUrl());
        existingProject.setFigmaKey(project.getFigmaKey());
        return projectRepository.save(existingProject);
    }

    @Override
    public void delete(Long id) {
        Project project = findById(id);
        projectRepository.delete(project);
    }
}