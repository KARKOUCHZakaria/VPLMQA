package com.figma.design.service;

import com.figma.design.dto.ComponentMappingDto;
import com.figma.design.dto.ComponentMappingDto.ComparisonResult;
import com.figma.design.dto.CreateComponentMappingRequest;
import com.figma.design.model.FigmaComponent;
import com.figma.design.model.WebComponent;
import com.figma.design.repository.FigmaComponentRepository;
import com.figma.design.repository.WebComponentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for managing component mappings between Figma designs and Web implementations
 * 
 * Key Responsibilities:
 * 1. Establish mappings between Figma and Web components
 * 2. Compare Figma design specs with Web implementation
 * 3. Provide component identification for E2E tests
 * 4. Track mapping status and changes
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ComponentMappingService {

    private final WebComponentRepository webComponentRepository;
    private final FigmaComponentRepository figmaComponentRepository;

    // ==================== Component Mapping ====================

    /**
     * Map a web component to a Figma component
     * 
     * @param request Mapping request containing component IDs and metadata
     * @return Updated component mapping DTO
     */
    public ComponentMappingDto mapComponents(CreateComponentMappingRequest request) {
        log.debug("Mapping web component {} to figma component {}", 
                  request.getWebComponentId(), request.getFigmaComponentId());

        // Get web component
        WebComponent webComponent = webComponentRepository.findById(request.getWebComponentId())
                .orElseThrow(() -> new RuntimeException("Web component not found: " + request.getWebComponentId()));

        // Get figma component
        FigmaComponent figmaComponent = null;
        if (request.getFigmaComponentId() != null) {
            figmaComponent = figmaComponentRepository.findById(request.getFigmaComponentId())
                    .orElseThrow(() -> new RuntimeException("Figma component not found: " + request.getFigmaComponentId()));
        } else if (request.getFigmaNodeId() != null) {
            // Try to find by figma node ID
            List<FigmaComponent> components = figmaComponentRepository.findAll();
            figmaComponent = components.stream()
                    .filter(c -> c.getFigmaNodeId().equals(request.getFigmaNodeId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Figma component not found by node ID: " + request.getFigmaNodeId()));
        }

        // Update web component with mapping info
        webComponent.setMappedFigmaComponentId(figmaComponent != null ? figmaComponent.getId() : null);
        webComponent.setFigmaNodeIdReference(figmaComponent != null ? figmaComponent.getFigmaNodeId() : request.getFigmaNodeId());
        webComponent.setTestIdentifier(validateTestIdentifier(request.getTestIdentifier()));
        webComponent.setFunctionalRole(request.getFunctionalRole());
        webComponent.setComponentDescription(request.getComponentDescription());
        webComponent.setFigmaComponentType(request.getFigmaComponentType());
        webComponent.setMappingStatus("MAPPED");
        webComponent.setMappedAt(LocalDateTime.now());
        webComponent.setMappingNotes(request.getMappingNotes());

        webComponentRepository.save(webComponent);

        // Perform comparison if requested
        ComponentMappingDto mappingDto = toComponentMappingDto(webComponent, figmaComponent);
        if (request.isPerformComparison() && figmaComponent != null) {
            ComparisonResult comparison = compareComponents(webComponent, figmaComponent);
            mappingDto.setComparisonResult(comparison);
        }

        log.info("Successfully mapped web component {} to figma component {}", 
                 request.getWebComponentId(), figmaComponent != null ? figmaComponent.getId() : "null");
        return mappingDto;
    }

    /**
     * Get mapping information for a web component
     * 
     * @param webComponentId ID of the web component
     * @return Component mapping DTO with all details
     */
    public ComponentMappingDto getComponentMapping(Long webComponentId) {
        WebComponent webComponent = webComponentRepository.findById(webComponentId)
                .orElseThrow(() -> new RuntimeException("Web component not found: " + webComponentId));

        FigmaComponent figmaComponent = null;
        if (webComponent.getMappedFigmaComponentId() != null) {
            figmaComponent = figmaComponentRepository.findById(webComponent.getMappedFigmaComponentId())
                    .orElse(null);
        }

        return toComponentMappingDto(webComponent, figmaComponent);
    }

    /**
     * Find web component by test identifier
     * 
     * @param testIdentifier The test identifier (e.g., "btn_login_submit")
     * @return Component mapping DTO if found
     */
    public Optional<ComponentMappingDto> findByTestIdentifier(String testIdentifier) {
        List<WebComponent> allComponents = webComponentRepository.findAll();
        Optional<WebComponent> webComponent = allComponents.stream()
                .filter(c -> testIdentifier.equals(c.getTestIdentifier()))
                .findFirst();

        if (webComponent.isEmpty()) {
            return Optional.empty();
        }

        WebComponent wc = webComponent.get();
        FigmaComponent figmaComponent = null;
        if (wc.getMappedFigmaComponentId() != null) {
            figmaComponent = figmaComponentRepository.findById(wc.getMappedFigmaComponentId())
                    .orElse(null);
        }

        return Optional.of(toComponentMappingDto(wc, figmaComponent));
    }

    /**
     * Find web component by functional role
     * 
     * @param functionalRole The functional role (e.g., "Login Button")
     * @return List of component mapping DTOs matching the role
     */
    public List<ComponentMappingDto> findByFunctionalRole(String functionalRole) {
        List<WebComponent> allComponents = webComponentRepository.findAll();
        List<ComponentMappingDto> results = new ArrayList<>();

        allComponents.stream()
                .filter(c -> functionalRole.equalsIgnoreCase(c.getFunctionalRole()))
                .forEach(wc -> {
                    FigmaComponent figmaComponent = null;
                    if (wc.getMappedFigmaComponentId() != null) {
                        figmaComponent = figmaComponentRepository.findById(wc.getMappedFigmaComponentId())
                                .orElse(null);
                    }
                    results.add(toComponentMappingDto(wc, figmaComponent));
                });

        return results;
    }

    /**
     * Search for components by multiple criteria
     * 
     * @param searchText Search across test identifier, functional role, component name, HTML ID
     * @return List of matching component mappings
     */
    public List<ComponentMappingDto> searchComponents(String searchText) {
        List<WebComponent> allComponents = webComponentRepository.findAll();
        List<ComponentMappingDto> results = new ArrayList<>();
        String lowerSearch = searchText.toLowerCase();

        allComponents.stream()
                .filter(c -> {
                    String testId = c.getTestIdentifier() != null ? c.getTestIdentifier().toLowerCase() : "";
                    String role = c.getFunctionalRole() != null ? c.getFunctionalRole().toLowerCase() : "";
                    String name = c.getComponentName() != null ? c.getComponentName().toLowerCase() : "";
                    String htmlId = c.getHtmlID() != null ? c.getHtmlID().toLowerCase() : "";
                    
                    return testId.contains(lowerSearch) || 
                           role.contains(lowerSearch) || 
                           name.contains(lowerSearch) || 
                           htmlId.contains(lowerSearch);
                })
                .forEach(wc -> {
                    FigmaComponent figmaComponent = null;
                    if (wc.getMappedFigmaComponentId() != null) {
                        figmaComponent = figmaComponentRepository.findById(wc.getMappedFigmaComponentId())
                                .orElse(null);
                    }
                    results.add(toComponentMappingDto(wc, figmaComponent));
                });

        return results;
    }

    // ==================== Component Comparison ====================

    /**
     * Compare Figma component design specifications with Web component implementation
     * 
     * @param webComponent Web implementation component
     * @param figmaComponent Figma design component
     * @return Detailed comparison result
     */
    public ComparisonResult compareComponents(WebComponent webComponent, FigmaComponent figmaComponent) {
        log.debug("Comparing web component {} with figma component {}", 
                  webComponent.getId(), figmaComponent.getId());

        List<ComponentMappingDto.Difference> differences = new ArrayList<>();
        
        // 1. Compare names (design spec vs implementation)
        if (!webComponent.getComponentName().equals(figmaComponent.getFigmaNodeName())) {
            differences.add(ComponentMappingDto.Difference.builder()
                    .aspect("name")
                    .figmaValue(figmaComponent.getFigmaNodeName())
                    .webValue(webComponent.getComponentName())
                    .severity("info")
                    .description("Component names differ between design and implementation")
                    .build());
        }

        // 2. Compare layout/position
        if (!Objects.equals(webComponent.getPositionX(), figmaComponent.getPositionX()) ||
            !Objects.equals(webComponent.getPositionY(), figmaComponent.getPositionY())) {
            differences.add(ComponentMappingDto.Difference.builder()
                    .aspect("layout")
                    .figmaValue(String.format("x:%s, y:%s", figmaComponent.getPositionX(), figmaComponent.getPositionY()))
                    .webValue(String.format("x:%s, y:%s", webComponent.getPositionX(), webComponent.getPositionY()))
                    .severity("warning")
                    .description("Position differs from design specification")
                    .build());
        }

        // 3. Compare dimensions
        if (!Objects.equals(webComponent.getWidth(), figmaComponent.getNodeWidth()) ||
            !Objects.equals(webComponent.getHeight(), figmaComponent.getNodeHeight())) {
            differences.add(ComponentMappingDto.Difference.builder()
                    .aspect("style")
                    .figmaValue(String.format("w:%s, h:%s", figmaComponent.getNodeWidth(), figmaComponent.getNodeHeight()))
                    .webValue(String.format("w:%s, h:%s", webComponent.getWidth(), webComponent.getHeight()))
                    .severity("warning")
                    .description("Dimensions differ from design specification")
                    .build());
        }

        // Calculate match percentage
        double matchPercentage = calculateMatchPercentage(differences.size());

        // Generate recommendation
        String recommendation = generateRecommendation(differences, matchPercentage);

        // Update web component with comparison results
        webComponent.setLastComparisonAt(LocalDateTime.now());
        webComponent.setMappingStatus(matchPercentage > 90 ? "MAPPED" : 
                                      matchPercentage > 70 ? "PARTIALLY_MAPPED" : "MISMATCH");
        
        ComparisonResult result = ComparisonResult.builder()
                .structureMatch(matchPercentage > 80)
                .styleMatch(matchPercentage > 85)
                .contentMatch(matchPercentage > 80)
                .differences(differences)
                .matchPercentage(matchPercentage)
                .recommendation(recommendation)
                .build();

        webComponentRepository.save(webComponent);
        log.info("Comparison complete: {}% match for web component {}", matchPercentage, webComponent.getId());

        return result;
    }

    // ==================== Helper Methods ====================

    /**
     * Validate test identifier format: snake_case
     */
    private String validateTestIdentifier(String testIdentifier) {
        if (testIdentifier == null || testIdentifier.isEmpty()) {
            return null;
        }

        // Validate snake_case format
        if (!Pattern.matches("^[a-z0-9_]+$", testIdentifier)) {
            log.warn("Invalid test identifier format: {}. Should be snake_case.", testIdentifier);
        }

        return testIdentifier;
    }

    /**
     * Calculate match percentage based on number of differences
     */
    private double calculateMatchPercentage(int differenceCount) {
        // Simple heuristic: 100% - (differenceCount * 10%)
        // Max 3 expected differences, so 100%, 90%, 80%, 70%...
        double percentage = 100 - (differenceCount * 10.0);
        return Math.max(0, Math.min(100, percentage));
    }

    /**
     * Generate recommendation text based on comparison results
     */
    private String generateRecommendation(List<ComponentMappingDto.Difference> differences, double matchPercentage) {
        if (matchPercentage > 90) {
            return "✓ Component implementation closely matches design specification";
        } else if (matchPercentage > 70) {
            return "⚠ Component implementation has some variations from design. Review and correct: " +
                   differences.stream()
                           .filter(d -> "warning".equals(d.getSeverity()))
                           .map(ComponentMappingDto.Difference::getDescription)
                           .distinct()
                           .limit(2)
                           .reduce((a, b) -> a + ", " + b)
                           .orElse("check differences");
        } else {
            return "✗ Component implementation significantly differs from design. Major review required.";
        }
    }

    /**
     * Convert entities to DTO
     */
    private ComponentMappingDto toComponentMappingDto(WebComponent webComponent, FigmaComponent figmaComponent) {
        Map<String, Object> figmaMetadata = new HashMap<>();
        if (figmaComponent != null) {
            figmaMetadata.put("positionX", figmaComponent.getPositionX());
            figmaMetadata.put("positionY", figmaComponent.getPositionY());
            figmaMetadata.put("width", figmaComponent.getNodeWidth());
            figmaMetadata.put("height", figmaComponent.getNodeHeight());
            figmaMetadata.put("nodeType", figmaComponent.getFigmaNodeType());
        }

        return ComponentMappingDto.builder()
                .webComponentId(webComponent.getId())
                .webComponentName(webComponent.getComponentName())
                .htmlTag(webComponent.getHtmlTag())
                .htmlId(webComponent.getHtmlID())
                .htmlClass(webComponent.getHtmlClass())
                .cssSelector(webComponent.getCssSelector())
                .functionalRole(webComponent.getFunctionalRole())
                .testIdentifier(webComponent.getTestIdentifier())
                .componentDescription(webComponent.getComponentDescription())
                .figmaComponentId(figmaComponent != null ? figmaComponent.getId() : null)
                .figmaNodeId(figmaComponent != null ? figmaComponent.getFigmaNodeId() : webComponent.getFigmaNodeIdReference())
                .figmaNodeName(figmaComponent != null ? figmaComponent.getFigmaNodeName() : null)
                .figmaNodeType(figmaComponent != null ? figmaComponent.getFigmaNodeType() : null)
                .figmaComponentType(webComponent.getFigmaComponentType())
                .figmaMetadata(figmaComponent != null && !figmaMetadata.isEmpty() ? figmaMetadata : null)
                .mappingStatus(webComponent.getMappingStatus())
                .mappedAt(webComponent.getMappedAt())
                .lastComparisonAt(webComponent.getLastComparisonAt())
                .mappingNotes(webComponent.getMappingNotes())
                .build();
    }
}
