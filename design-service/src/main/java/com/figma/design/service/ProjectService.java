package com.figma.design.service;

import com.figma.design.model.Project;
import java.util.List;

public interface ProjectService {

    List<Project> findAll();

    Project findById(Long id);

    Project create(Project project);

    Project update(Long id, Project project);

    void delete(Long id);
}