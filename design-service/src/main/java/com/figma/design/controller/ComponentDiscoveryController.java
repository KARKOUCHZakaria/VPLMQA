package com.figma.design.controller;

import com.figma.design.service.ComponentDiscoveryService;
import com.figma.design.service.ComponentDiscoveryService.ComponentMetadata;
import com.figma.design.service.ComponentDiscoveryService.ComponentMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for discovering and matching components from folder structure
 * 
 * Purpose:
 * - Discover Figma components in a page folder
 * - Discover Web components in a page folder
 * - Match Figma ↔ Web components automatically
 * - Generate identification guides for E2E testing
 */
@RestController
@RequestMapping("/api/v1/components/discovery")
@RequiredArgsConstructor
@Slf4j
public class ComponentDiscoveryController {

    private final ComponentDiscoveryService discoveryService;

    /**
     * Discover all Figma components in a specific page folder
     * 
     * Endpoint: GET /api/v1/components/discovery/figma-components?pagePath=pages/login-page
     * 
     * Response includes:
     * - Component name
     * - Component type (Button, Card, Form, etc.)
     * - Functional role (what it does)
     * - File path
     * - Component properties (color, size, etc.)
     * 
     * @param pagePath Path to page folder (e.g., "pages/login-page")
     * @return List of discovered Figma components with metadata
     */
    @GetMapping("/figma-components")
    public ResponseEntity<List<ComponentMetadata>> discoverFigmaComponents(
            @RequestParam String pagePath) {
        
        log.info("Discovering Figma components in: {}", pagePath);
        List<ComponentMetadata> components = discoveryService.discoverFigmaComponents(pagePath);
        
        return ResponseEntity.ok(components);
    }

    /**
     * Discover all Web components in a specific page folder
     * 
     * Endpoint: GET /api/v1/components/discovery/web-components?pagePath=pages/login-page
     * 
     * Response includes:
     * - Component name
     * - Component type (Button, Card, Form, etc.)
     * - Functional role (what it does)
     * - HTML tag, ID, class, CSS selector
     * - File path
     * 
     * @param pagePath Path to page folder (e.g., "pages/login-page")
     * @return List of discovered Web components with metadata
     */
    @GetMapping("/web-components")
    public ResponseEntity<List<ComponentMetadata>> discoverWebComponents(
            @RequestParam String pagePath) {
        
        log.info("Discovering Web components in: {}", pagePath);
        List<ComponentMetadata> components = discoveryService.discoverWebComponents(pagePath);
        
        return ResponseEntity.ok(components);
    }

    /**
     * Automatically match Figma components with Web components on the same page
     * 
     * This endpoint:
     * 1. Discovers all Figma components in the page folder
     * 2. Discovers all Web components in the page folder
     * 3. Automatically matches them based on:
     *    - Component name similarity
     *    - Component type match
     *    - Functional role match
     * 4. Returns matches with confidence score
     * 
     * Endpoint: POST /api/v1/components/discovery/match?pagePath=pages/login-page
     * 
     * Response includes:
     * {
     *   "figmaComponent": { name, type, role, properties },
     *   "webComponent": { name, type, role, cssSelector, htmlId },
     *   "matchScore": 85,  // 0-100% confidence
     *   "matchReasons": ["Component names match", "Types match"],
     *   "differences": ["Width differs: 300 vs 320"]
     * }
     * 
     * Usage for E2E Tests:
     * 1. Call this endpoint to get component mappings
     * 2. Use the webComponent.cssSelector to locate elements
     * 3. Use webComponent.functionalRole to understand what to test
     * 4. Use matchScore to verify this is the right component
     * 
     * @param pagePath Path to page folder (e.g., "pages/login-page")
     * @return List of component matches (Figma ↔ Web)
     */
    @PostMapping("/match")
    public ResponseEntity<Map<String, Object>> matchComponents(
            @RequestParam String pagePath) {
        
        log.info("Matching components for page: {}", pagePath);
        
        // Discover components
        List<ComponentMetadata> figmaComponents = discoveryService.discoverFigmaComponents(pagePath);
        List<ComponentMetadata> webComponents = discoveryService.discoverWebComponents(pagePath);
        
        // Match components
        List<ComponentMatch> matches = discoveryService.matchComponents(figmaComponents, webComponents);
        
        // Generate identification guide
        String guide = discoveryService.generateComponentGuide(matches, pagePath);
        
        log.info("Found {} matches for page: {}", matches.size(), pagePath);
        
        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("pagePath", pagePath);
        response.put("figmaComponentsCount", figmaComponents.size());
        response.put("webComponentsCount", webComponents.size());
        response.put("matchesCount", matches.size());
        response.put("matches", matches);
        response.put("identificationGuide", guide);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed component identification guide for a page
     * 
     * This generates a human-readable guide showing:
     * - Each matched component pair (Figma ↔ Web)
     * - Match confidence percentage
     * - Properties of both components
     * - How to use in E2E tests
     * - Any differences found
     * 
     * @param pagePath Path to page folder
     * @return Text guide for identifying and using components
     */
    @GetMapping("/identification-guide")
    public ResponseEntity<String> getIdentificationGuide(
            @RequestParam String pagePath) {
        
        log.info("Generating identification guide for: {}", pagePath);
        
        List<ComponentMetadata> figmaComponents = discoveryService.discoverFigmaComponents(pagePath);
        List<ComponentMetadata> webComponents = discoveryService.discoverWebComponents(pagePath);
        List<ComponentMatch> matches = discoveryService.matchComponents(figmaComponents, webComponents);
        
        String guide = discoveryService.generateComponentGuide(matches, pagePath);
        
        return ResponseEntity.ok(guide);
    }
}
