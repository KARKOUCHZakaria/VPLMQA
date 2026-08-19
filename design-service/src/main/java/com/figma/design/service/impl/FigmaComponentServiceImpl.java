package com.figma.design.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.figma.FigmaDesignImportRequest;
import com.figma.design.dto.figma.FigmaDesignImportResponse;
import com.figma.design.dto.figma.ProjectDesignImportRequest;
import com.figma.design.exception.FigmaIntegrationException;
import com.figma.design.exception.ResourceNotFoundException;
import com.figma.design.model.ComponentType;
import com.figma.design.model.FigmaComponent;
import com.figma.design.model.Page;
import com.figma.design.model.Project;
import com.figma.design.repository.FigmaComponentRepository;
import com.figma.design.repository.PageRepository;
import com.figma.design.repository.ProjectRepository;
import com.figma.design.service.FigmaComponentService;
import com.figma.design.service.MinIOService;
import com.figma.design.service.PageFileGeneratorService;
import com.figma.design.util.FileNameSanitizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FigmaComponentServiceImpl implements FigmaComponentService {

    private final FigmaComponentRepository figmaComponentRepository;
    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final MinIOService minIOService;
    private final PageFileGeneratorService pageFileGeneratorService;

    @Value("${figma.api.base-url:https://api.figma.com}")
    private String figmaBaseUrl;

    @Value("${figma.api.token:}")
    private String configuredApiToken;

    @Override
    @Transactional(readOnly = true)
    public List<FigmaComponent> findAll() {
        return figmaComponentRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public FigmaComponent findById(Long id) {
        return figmaComponentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FigmaComponent", id));
    }

    @Override
    public FigmaComponent create(FigmaComponent figmaComponent, Long pageId) {
        figmaComponent.setType(ComponentType.FIGMA);
        figmaComponent.setPage(resolvePage(figmaComponent, pageId));
        return figmaComponentRepository.save(figmaComponent);
    }

    @Override
    public FigmaComponent update(Long id, FigmaComponent figmaComponent, Long pageId) {
        FigmaComponent existingComponent = findById(id);
        existingComponent.setType(ComponentType.FIGMA);
        existingComponent.setPage(resolvePage(figmaComponent, pageId, existingComponent.getPage()));
        return figmaComponentRepository.save(existingComponent);
    }

    @Override
    public void delete(Long id) {
        FigmaComponent figmaComponent = findById(id);
        figmaComponentRepository.delete(figmaComponent);
    }

    @Override
    public FigmaDesignImportResponse importFromFigma(FigmaDesignImportRequest request) {
        String fileKey = request.resolveFileKey();
        if (!StringUtils.hasText(fileKey)) {
            throw new IllegalArgumentException("fileKey or fileUrl is required");
        }

        String apiToken = resolveApiToken(request);
        JsonNode fileJson = fetchFigmaFile(fileKey, apiToken);
        if (fileJson == null) {
            throw new FigmaIntegrationException("Figma API returned an empty response");
        }

        JsonNode documentNode = fileJson.path("document");
        if (documentNode.isMissingNode() || documentNode.isNull()) {
            throw new IllegalArgumentException("Figma API response does not contain a document node");
        }

        String fileName = FileNameSanitizer.sanitize(fileJson.path("name").asText(fileKey));
        
        List<FigmaDesignImportResponse.ImportedPage> pages = new ArrayList<>();
        List<ImportedComponent> importedComponents = new ArrayList<>();

        JsonNode children = documentNode.path("children");
        if (children.isArray()) {
            for (JsonNode canvasNode : children) {
                if (!"CANVAS".equalsIgnoreCase(canvasNode.path("type").asText())) {
                    continue;
                }

                List<FigmaDesignImportResponse.ImportedComponent> pageComponents = new ArrayList<>();
                collectComponents(canvasNode, pageComponents, importedComponents);
                pages.add(new FigmaDesignImportResponse.ImportedPage(
                        canvasNode.path("id").asText(null),
                        canvasNode.path("name").asText("Unnamed page"),
                        pageComponents));
            }
        }

        int persistedComponents = 0;
        Page targetPage = null;
        if (request.shouldPersist()) {
            Long pageId = request.pageId();
            if (pageId == null) {
                throw new IllegalArgumentException("pageId is required when persist is enabled");
            }

            targetPage = pageRepository.findById(pageId)
                    .orElseThrow(() -> new ResourceNotFoundException("Page", pageId));
            figmaComponentRepository.deleteByPage_Id(pageId);

            final Page finalTargetPage = targetPage;
            List<FigmaComponent> entities = importedComponents.stream()
                    .map(component -> toEntity(component, finalTargetPage, fileKey, fileName))
                    .toList();
            persistedComponents = figmaComponentRepository.saveAll(entities).size();
        }

        // Upload the response JSON to MinIO first to get the path
        String minioPath = null;
        try {
            String responseJson = objectMapper.writeValueAsString(
                    new FigmaDesignImportResponse(fileKey, fileName, pages, importedComponents.size(), persistedComponents, null)
            );
            minioPath = minIOService.uploadDesign(fileName, responseJson);
            
            // Store MinIO path in the project if we persisted
            if (targetPage != null && minioPath != null) {
                Project project = targetPage.getProject();
                if (project != null) {
                    project.setLinkMinIO(minioPath);
                    projectRepository.saveAndFlush(project);
                }
            }
        } catch (Exception exception) {
            // Log the error but don't fail the import process
            throw new FigmaIntegrationException("Failed to upload design to MinIO: " + exception.getMessage());
        }
        
        // Build the final response with MinIO path
        FigmaDesignImportResponse response = new FigmaDesignImportResponse(
                fileKey, 
                fileName, 
                pages, 
                importedComponents.size(), 
                persistedComponents,
                minioPath
        );
        
        return response;
    }

    private JsonNode fetchFigmaFile(String fileKey, String apiToken) {
        try {
            return webClientBuilder
                    .baseUrl(figmaBaseUrl)
                    .defaultHeader("X-Figma-Token", apiToken)
                    .build()
                    .get()
                    .uri("/v1/files/{fileKey}", fileKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw new FigmaIntegrationException(buildFigmaErrorMessage(exception));
        }
    }

    private String buildFigmaErrorMessage(WebClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(responseBody)) {
            responseBody = exception.getMessage();
        }
        return "Figma API error (%s): %s".formatted(exception.getStatusCode().value(), responseBody);
    }

    private String resolveApiToken(FigmaDesignImportRequest request) {
        if (StringUtils.hasText(request.apiToken())) {
            return request.apiToken().trim();
        }

        if (StringUtils.hasText(configuredApiToken)) {
            return configuredApiToken.trim();
        }

        throw new IllegalArgumentException("Figma API token is required either in the request or in figma.api.token");
    }

    private String resolveApiToken(String token, boolean isProvidedToken) {
        if (isProvidedToken && StringUtils.hasText(token)) {
            return token.trim();
        }

        if (StringUtils.hasText(configuredApiToken)) {
            return configuredApiToken.trim();
        }

        throw new IllegalArgumentException("Figma API token is required either in the request or in figma.api.token");
    }

    private void collectComponents(JsonNode node,
                                   List<FigmaDesignImportResponse.ImportedComponent> pageComponents,
                                   List<ImportedComponent> importedComponents) {
        String nodeType = node.path("type").asText("");
        if (!"DOCUMENT".equalsIgnoreCase(nodeType) && !"CANVAS".equalsIgnoreCase(nodeType)) {
            ImportedComponent component = new ImportedComponent(
                    node.path("id").asText(null),
                    node.path("name").asText("Unnamed node"),
                    nodeType,
                    readBoundingValue(node, "x"),
                    readBoundingValue(node, "y"),
                    readBoundingValue(node, "width"),
                    readBoundingValue(node, "height"),
                    node.path("characters").isMissingNode() ? null : node.path("characters").asText(null),
                    safeJson(node));

            pageComponents.add(new FigmaDesignImportResponse.ImportedComponent(
                    component.id(),
                    component.name(),
                    component.type(),
                    component.x(),
                    component.y(),
                    component.width(),
                    component.height(),
                    component.text()));
            importedComponents.add(component);
        }

        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                collectComponents(child, pageComponents, importedComponents);
            }
        }
    }

    private Double readBoundingValue(JsonNode node, String fieldName) {
        JsonNode boundingBox = node.path("absoluteBoundingBox");
        if (boundingBox.isMissingNode() || boundingBox.isNull() || !boundingBox.hasNonNull(fieldName)) {
            return null;
        }
        return boundingBox.get(fieldName).asDouble();
    }

    private String safeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return node.toString();
        }
    }

    private FigmaComponent toEntity(ImportedComponent component, Page targetPage, String fileKey, String fileName) {
        FigmaComponent entity = new FigmaComponent();
        entity.setType(ComponentType.FIGMA);
        entity.setPage(targetPage);
        entity.setFigmaNodeId(component.id());
        entity.setFigmaNodeName(component.name());
        entity.setFigmaNodeType(component.type());
        entity.setPositionX(component.x());
        entity.setPositionY(component.y());
        entity.setNodeWidth(component.width());
        entity.setNodeHeight(component.height());
        entity.setSourceFileKey(fileKey);
        entity.setSourceFileName(fileName);
        entity.setRawJson(component.rawJson());
        entity.setImportedAt(LocalDateTime.now());
        return entity;
    }

    private record ImportedComponent(
            String id,
            String name,
            String type,
            Double x,
            Double y,
            Double width,
            Double height,
            String text,
            String rawJson) {
    }

    private Page resolvePage(FigmaComponent figmaComponent, Long pageId) {
        return resolvePage(figmaComponent, pageId, null);
    }

    private Page resolvePage(FigmaComponent figmaComponent, Long pageId, Page fallbackPage) {
        Long resolvedPageId = pageId;
        if (resolvedPageId == null && figmaComponent != null && figmaComponent.getPage() != null) {
            resolvedPageId = figmaComponent.getPage().getId();
        }

        if (resolvedPageId == null) {
            if (fallbackPage != null) {
                return fallbackPage;
            }
            throw new IllegalArgumentException("pageId is required");
        }

        Long finalResolvedPageId = resolvedPageId;
        return pageRepository.findById(finalResolvedPageId)
            .orElseThrow(() -> new ResourceNotFoundException("Page", finalResolvedPageId));
    }

    @Override
    public FigmaDesignImportResponse importDesignForProject(ProjectDesignImportRequest request) {
        String fileKey = request.resolveFileKey();
        if (!StringUtils.hasText(fileKey)) {
            throw new IllegalArgumentException("fileKey or fileUrl is required");
        }

        if (request.projectId() == null) {
            throw new IllegalArgumentException("projectId is required");
        }

        // Fetch the project
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        String apiToken = resolveApiToken(request.apiToken(), request.apiToken() != null);
        JsonNode fileJson = fetchFigmaFile(fileKey, apiToken);
        if (fileJson == null) {
            throw new FigmaIntegrationException("Figma API returned an empty response");
        }

        JsonNode documentNode = fileJson.path("document");
        if (documentNode.isMissingNode() || documentNode.isNull()) {
            throw new IllegalArgumentException("Figma API response does not contain a document node");
        }

        String fileName = FileNameSanitizer.sanitize(fileJson.path("name").asText(fileKey));
        
        List<FigmaDesignImportResponse.ImportedPage> pages = new ArrayList<>();
        List<ImportedComponent> importedComponents = new ArrayList<>();

        JsonNode children = documentNode.path("children");
        if (children.isArray()) {
            for (JsonNode canvasNode : children) {
                if (!"CANVAS".equalsIgnoreCase(canvasNode.path("type").asText())) {
                    continue;
                }

                List<FigmaDesignImportResponse.ImportedComponent> pageComponents = new ArrayList<>();
                collectComponents(canvasNode, pageComponents, importedComponents);
                pages.add(new FigmaDesignImportResponse.ImportedPage(
                        canvasNode.path("id").asText(null),
                        canvasNode.path("name").asText("Unnamed page"),
                        pageComponents));
            }
        }

        // Upload the design file to MinIO
        String minioPath = null;
        try {
            String responseJson = objectMapper.writeValueAsString(
                    new FigmaDesignImportResponse(fileKey, fileName, pages, importedComponents.size(), 0, null)
            );
            minioPath = minIOService.uploadDesign(fileName, responseJson);
            
            // Update project with MinIO link
            project.setLinkMinIO(minioPath);
            project.setFigmaDesignFileId(fileKey);
            project.setDesignImplementationStatus("IMPORTED");
            project.setLastDesignSyncAt(LocalDateTime.now());
            projectRepository.saveAndFlush(project);
            
            log.info("Design imported for project '{}' and stored at: {}", project.getName(), minioPath);
        } catch (Exception exception) {
            log.error("Failed to upload design to MinIO for project: {}", project.getName(), exception);
            throw new FigmaIntegrationException("Failed to upload design to MinIO: " + exception.getMessage());
        }

        // Build response with MinIO path
        return new FigmaDesignImportResponse(
                fileKey,
                fileName,
                pages,
                importedComponents.size(),
                0,
                minioPath
        );
    }

    @Override
    @Transactional
    public List<com.figma.design.model.Page> generatePagesFromDesign(Long projectId) {
        // Fetch the project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        // Check if project has design file stored in MinIO
        String minioLink = project.getLinkMinIO();
        if (minioLink == null || minioLink.isEmpty()) {
            throw new FigmaIntegrationException("Project has no design file. Please import design first.");
        }

        try {
            // Download design file from MinIO
            String designJson = minIOService.downloadDesign(minioLink);
            
            // Parse JSON back to FigmaDesignImportResponse
            FigmaDesignImportResponse designResponse = objectMapper.readValue(designJson, FigmaDesignImportResponse.class);
            
            log.info("Downloaded design file for project: {} from MinIO", project.getName());
            
            // Generate pages from design
            List<com.figma.design.model.Page> pages = pageFileGeneratorService.generatePagesFromDesign(projectId, designResponse);
            
            log.info("Generated {} pages for project: {}", pages.size(), project.getName());
            
            return pages;
            
        } catch (Exception exception) {
            log.error("Failed to generate pages from design for project: {}", projectId, exception);
            throw new FigmaIntegrationException("Failed to generate pages from design: " + exception.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FigmaComponent> extractComponentsFromFigmaPage(String figmaPageId) throws Exception {
        /**
         * Extract components from a Figma page by ID
         * TODO: Implement Figma API integration to fetch page structure
         * This method should:
         * 1. Call Figma API to fetch page structure
         * 2. Parse the component hierarchy
         * 3. Extract metadata (position, size, type, properties)
         * 4. Return list of FigmaComponent objects (not persisted)
         */
        return new ArrayList<>();
    }
}