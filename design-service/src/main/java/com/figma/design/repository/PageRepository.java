package com.figma.design.repository;

import com.figma.design.model.Page;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRepository extends JpaRepository<Page, Long> {
	Optional<Page> findByFigmaPageId(String figmaPageId);
	
	/**
	 * Find all pages for a given project
	 * @param projectId Project ID
	 * @return List of pages for the project
	 */
	List<Page> findByProjectId(Long projectId);
}