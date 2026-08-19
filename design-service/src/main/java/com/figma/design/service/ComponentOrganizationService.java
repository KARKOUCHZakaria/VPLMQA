package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComponentOrganizationService {

    private final WebClient projectServiceClient;
    private final DesignTokenMlInferenceService agentService;
    private final ObjectMapper objectMapper;

    public ComponentOrganizationService(@Qualifier("projectServiceClient") WebClient projectServiceClient,
                                        DesignTokenMlInferenceService agentService,
                                        ObjectMapper objectMapper) {
        this.projectServiceClient = projectServiceClient;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> organize(UUID projectId, UUID pageId, String workflowContext) {
        JsonNode pages = getData("/api/v1/projects/" + projectId + "/pages");
        JsonNode selectedPage = null;
        Map<String, String> pageNames = new LinkedHashMap<>();
        for (JsonNode page : pages) {
            pageNames.put(page.path("id").asText(), page.path("name").asText(""));
            if (page.path("id").asText().equals(pageId.toString())) selectedPage = page;
        }
        if (selectedPage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected page does not belong to this project.");
        }
        String pageName = selectedPage.path("name").asText("page");
        String pageKey = matchKey(pageName);
        List<Map<String, Object>> figmaComponents = new ArrayList<>();
        List<Map<String, Object>> webComponents = new ArrayList<>();
        for (Map.Entry<String, String> page : pageNames.entrySet()) {
            if (!pageKey.equals(matchKey(page.getValue()))) continue;
            JsonNode components = getData("/api/v1/projects/" + projectId + "/components/page/" + page.getKey());
            for (JsonNode component : components) {
                Map<String, Object> value = objectMapper.convertValue(component, Map.class);
                if ("FIGMA".equalsIgnoreCase(component.path("source").asText())) figmaComponents.add(value);
                if ("WEB".equalsIgnoreCase(component.path("source").asText())) webComponents.add(value);
            }
        }
        if (webComponents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Extract the Web page before organizing its E2E components.");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("page_name", pageName);
        request.put("figma_components", figmaComponents);
        request.put("web_components", webComponents);
        request.put("workflow_context", workflowContext == null ? "" : workflowContext);
        JsonNode result = agentService.organizeComponents(request);

        int persistedCount = persistMappings(projectId, result.path("mappings"));

        Map<String, Object> response = objectMapper.convertValue(result, Map.class);
        response.put("projectId", projectId);
        response.put("pageId", pageId);
        response.put("persistedComponentCount", persistedCount);
        return response;
    }

    public int persistMappings(UUID projectId, JsonNode mappings) {
        Map<String, Map<String, Object>> updates = new LinkedHashMap<>();
        for (JsonNode mapping : mappings) {
            String uniqueName = mapping.path("uniqueName").asText("");
            String role = mapping.path("role").asText("interactive component");
            String usage = mapping.path("usage").asText("");
            addUpdate(updates, mapping.path("figmaComponentId").asText(""), uniqueName, role, usage);
            addUpdate(updates, mapping.path("webComponentId").asText(""), uniqueName, role, usage);
        }
        if (!updates.isEmpty()) {
            JsonNode envelope = projectServiceClient.post()
                    .uri("/api/v1/projects/" + projectId + "/components/semantic/bulk")
                    .bodyValue(new ArrayList<>(updates.values()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (envelope == null || !envelope.path("success").asBoolean(false)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Project service failed to persist semantic component mappings.");
            }
        }

        return updates.size();
    }

    private void addUpdate(Map<String, Map<String, Object>> updates, String componentId,
                           String uniqueName, String role, String usage) {
        if (componentId == null || componentId.isBlank() || uniqueName.isBlank()) return;
        updates.put(componentId, Map.of(
                "componentId", componentId,
                "canonicalName", uniqueName,
                "semanticRole", role,
                "functionalMeaning", usage
        ));
    }

    private JsonNode getData(String path) {
        JsonNode envelope = projectServiceClient.get().uri(path).retrieve().bodyToMono(JsonNode.class).block();
        if (envelope == null || !envelope.path("success").asBoolean(false)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Project service request failed: " + path);
        }
        return envelope.path("data");
    }

    private String matchKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }
}
