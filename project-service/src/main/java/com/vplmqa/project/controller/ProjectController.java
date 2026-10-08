package com.vplmqa.project.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.project.dto.PageRequest;
import com.vplmqa.project.dto.PageResponse;
import com.vplmqa.project.dto.ProjectRequest;
import com.vplmqa.project.dto.ProjectResponse;
import com.vplmqa.project.dto.ProjectSettingsRequest;
import com.vplmqa.project.dto.ProjectSettingsResponse;
import com.vplmqa.project.service.PageService;
import com.vplmqa.project.service.ProjectService;
import com.vplmqa.project.service.ProjectSettingsService;
import com.vplmqa.project.service.ProjectAutomationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for project lifecycle operations.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

	private final ProjectService projectService;
	private final PageService pageService;
	private final ProjectSettingsService projectSettingsService;
	private final ProjectAutomationService projectAutomationService;

	/**
	 * Creates the controller.
	 *
	 * @param projectService project service
	 * @param pageService page service
	 * @param projectSettingsService settings service
	 */
	public ProjectController(ProjectService projectService,
							 PageService pageService,
							 ProjectSettingsService projectSettingsService,
							 ProjectAutomationService projectAutomationService) {
		this.projectService = projectService;
		this.pageService = pageService;
		this.projectSettingsService = projectSettingsService;
		this.projectAutomationService = projectAutomationService;
	}

	/**
	 * Lists projects for the current user.
	 *
	 * @param userId current user id header
	 * @return project list
	 */
	@GetMapping
	public ResponseEntity<ApiResponse<List<ProjectResponse>>> list(@RequestHeader("X-User-Id") UUID userId) {
		return ResponseEntity.ok(ApiResponse.ok(projectService.getProjectsByUser(userId)));
	}

	/**
	 * Creates a project.
	 *
	 * @param request request body
	 * @param userId current user id header
	 * @return created project
	 */
	@PostMapping
	public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody ProjectRequest request,
															   @RequestHeader("X-User-Id") UUID userId) {
		ProjectResponse created = projectService.createProject(request, userId);
		boolean hasFigmaConfiguration = request.figmaFileUrl() != null
				&& !request.figmaFileUrl().isBlank()
				&& request.figmaTokenEncrypted() != null
				&& !request.figmaTokenEncrypted().isBlank();
		if (hasFigmaConfiguration) {
			projectAutomationService.startProjectExtraction(created.id());
			return ResponseEntity.ok(ApiResponse.ok(created, "Project created; Figma extraction started."));
		}
		return ResponseEntity.ok(ApiResponse.ok(created, "E2E project created."));
	}

	/**
	 * Returns a project.
	 *
	 * @param id project id
	 * @return project response
	 */
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.ok(projectService.getProject(id)));
	}

	/**
	 * Updates a project.
	 *
	 * @param id project id
	 * @param request request body
	 * @return project response
	 */
	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<ProjectResponse>> update(@PathVariable UUID id,
															   @Valid @RequestBody ProjectRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(projectService.updateProject(id, request)));
	}

	/**
	 * Archives a project.
	 *
	 * @param id project id
	 * @return confirmation response
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> archive(@PathVariable UUID id) {
		projectService.archiveProject(id);
		return ResponseEntity.ok(ApiResponse.ok(null, "Project archived"));
	}

	/**
	 * Lists pages for a project.
	 *
	 * @param id project id
	 * @return page list
	 */
	@GetMapping("/{id}/pages")
	public ResponseEntity<ApiResponse<List<PageResponse>>> listPages(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.ok(pageService.listPages(id)));
	}

	/**
	 * Adds a page to a project.
	 *
	 * @param id project id
	 * @param request request body
	 * @return created page
	 */
	@PostMapping("/{id}/pages")
	public ResponseEntity<ApiResponse<PageResponse>> addPage(@PathVariable UUID id,
															 @Valid @RequestBody PageRequest request) {
		PageResponse created = pageService.createPage(id, request);
		projectAutomationService.startWebPageWorkflow(id, created.id());
		return ResponseEntity.ok(ApiResponse.ok(created, "Page saved; extraction and comparison started."));
	}
	@PutMapping("/{id}/pages/{pageId}")
	public ResponseEntity<ApiResponse<PageResponse>> updatePage(@PathVariable UUID id,
															 @PathVariable UUID pageId,
															 @Valid @RequestBody PageRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(pageService.updatePage(id, pageId, request)));
	}

	/**
	 * Deletes a page from a project.
	 *
	 * @param id project id
	 * @param pageId page id
	 * @return confirmation response
	 */
	@DeleteMapping("/{id}/pages/{pageId}")
	public ResponseEntity<ApiResponse<Void>> deletePage(@PathVariable UUID id, @PathVariable UUID pageId) {
		pageService.deletePage(id, pageId);
		return ResponseEntity.ok(ApiResponse.ok(null, "Page deleted"));
	}

	/**
	 * Returns settings for a project.
	 *
	 * @param id project id
	 * @return project settings
	 */
	@GetMapping("/{id}/settings")
	public ResponseEntity<ApiResponse<ProjectSettingsResponse>> getSettings(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.ok(projectSettingsService.getSettings(id)));
	}

	/**
	 * Updates settings for a project.
	 *
	 * @param id project id
	 * @param request request body
	 * @return updated settings
	 */
	@PutMapping("/{id}/settings")
	public ResponseEntity<ApiResponse<ProjectSettingsResponse>> updateSettings(@PathVariable UUID id,
																			   @RequestBody ProjectSettingsRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(projectSettingsService.updateSettings(id, request)));
	}
}
