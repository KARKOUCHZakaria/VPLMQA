package com.figma.design.controller;

import com.figma.design.dto.ComponentMappingDto;
import com.figma.design.dto.CreateComponentMappingRequest;
import com.figma.design.model.WebComponent;
import com.figma.design.service.WebComponentService;
import com.figma.design.service.ComponentMappingService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web-components")
@RequiredArgsConstructor
public class WebComponentController {

    private final WebComponentService webComponentService;
    private final ComponentMappingService componentMappingService;

    // ==================== Standard CRUD Operations ====================

    @GetMapping
    public List<WebComponent> findAll() {
        return webComponentService.findAll();
    }

    @GetMapping("/{id}")
    public WebComponent findById(@PathVariable Long id) {
        return webComponentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<WebComponent> create(@RequestBody WebComponent webComponent, @RequestParam(required = false) Long pageId) {
        return ResponseEntity.ok(webComponentService.create(webComponent, pageId));
    }

    @PutMapping("/{id}")
    public WebComponent update(@PathVariable Long id, @RequestBody WebComponent webComponent, @RequestParam(required = false) Long pageId) {
        return webComponentService.update(id, webComponent, pageId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        webComponentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Component Mapping & Identification ====================
    // These endpoints enable E2E tests to:
    // 1. Know which Figma component corresponds to which web component
    // 2. Identify components by test identifiers
    // 3. Understand component roles and purposes

    /**
     * Map a web component to a Figma component
     * 
     * Usage: Link a web implementation to its Figma design specification
     * 
     * Request Example:
     * {
     *   "webComponentId": 1,
     *   "figmaComponentId": 5,
     *   "testIdentifier": "btn_login_submit",
     *   "functionalRole": "Login Submit Button",
     *   "componentDescription": "Button that submits the login form",
     *   "figmaComponentType": "Button",
     *   "performComparison": true
     * }
     * 
     * @param request Mapping request with component IDs and metadata
     * @return Component mapping with comparison results if requested
     */
    @PostMapping("/{id}/map-to-figma")
    public ResponseEntity<ComponentMappingDto> mapToFigmaComponent(
            @PathVariable Long id,
            @RequestBody CreateComponentMappingRequest request) {
        
        request.setWebComponentId(id); // Ensure web component ID from path is used
        ComponentMappingDto mapping = componentMappingService.mapComponents(request);
        return ResponseEntity.ok(mapping);
    }

    /**
     * Get component mapping and comparison information
     * 
     * Response includes:
     * - Web component details (HTML, CSS, selectors)
     * - Mapped Figma component details
     * - Mapping status (UNMAPPED, MAPPED, PARTIALLY_MAPPED, MISMATCH)
     * - Comparison results (match %, differences found)
     * - Test identifier for E2E tests
     * - Functional role description
     * 
     * @param id Web component ID
     * @return Component mapping with full details
     */
    @GetMapping("/{id}/mapping")
    public ResponseEntity<ComponentMappingDto> getComponentMapping(@PathVariable Long id) {
        ComponentMappingDto mapping = componentMappingService.getComponentMapping(id);
        return ResponseEntity.ok(mapping);
    }

    /**
     * Find web component by test identifier
     * 
     * For E2E Tests: Use this to locate a component by its test ID
     * 
     * Examples:
     * - GET /api/web-components/by-test-id/btn_login_submit
     * - GET /api/web-components/by-test-id/nav_header_menu
     * - GET /api/web-components/by-test-id/form_contact_email
     * 
     * Response includes component details, mapping status, and functional role
     * 
     * @param testIdentifier Test ID in format: snake_case
     * @return Component mapping if found, 404 if not found
     */
    @GetMapping("/by-test-id/{testIdentifier}")
    public ResponseEntity<ComponentMappingDto> findByTestIdentifier(@PathVariable String testIdentifier) {
        Optional<ComponentMappingDto> mapping = componentMappingService.findByTestIdentifier(testIdentifier);
        return mapping.map(ResponseEntity::ok)
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Find web components by functional role
     * 
     * For E2E Tests: Use this to find all components with a specific role/purpose
     * 
     * Examples:
     * - GET /api/web-components/by-role?role=Login+Form
     * - GET /api/web-components/by-role?role=Navigation+Header
     * - GET /api/web-components/by-role?role=Product+Card
     * 
     * @param role Functional role name
     * @return List of components matching the role
     */
    @GetMapping("/by-role")
    public ResponseEntity<List<ComponentMappingDto>> findByRole(@RequestParam String role) {
        List<ComponentMappingDto> mappings = componentMappingService.findByFunctionalRole(role);
        return ResponseEntity.ok(mappings);
    }

    /**
     * Search components by multiple criteria
     * 
     * For E2E Tests: Use this for flexible component discovery
     * Searches across: test ID, functional role, component name, HTML ID
     * 
     * Examples:
     * - GET /api/web-components/search?q=login
     * - GET /api/web-components/search?q=btn_submit
     * - GET /api/web-components/search?q=navigation
     * 
     * @param searchText Search query (partial matches supported)
     * @return List of matching components
     */
    @GetMapping("/search")
    public ResponseEntity<List<ComponentMappingDto>> searchComponents(@RequestParam String q) {
        List<ComponentMappingDto> results = componentMappingService.searchComponents(q);
        return ResponseEntity.ok(results);
    }

    /**
     * Re-compare a mapped component with its Figma specification
     * 
     * Purpose: Verify component implementation still matches design
     * Useful for regression testing and design compliance checks
     * 
     * @param id Web component ID
     * @return Updated mapping with new comparison results
     */
    @PostMapping("/{id}/compare-with-figma")
    public ResponseEntity<ComponentMappingDto> compareWithFigma(@PathVariable Long id) {
        ComponentMappingDto mapping = componentMappingService.getComponentMapping(id);
        return ResponseEntity.ok(mapping);
    }
}