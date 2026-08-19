package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
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
public class PageEnrichmentService {

    private final WebClient projectServiceClient;
    private final DesignTokenMlInferenceService agentService;
    private final ComponentOrganizationService organizationService;
    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;

    public PageEnrichmentService(@Qualifier("projectServiceClient") WebClient projectServiceClient,
                                 DesignTokenMlInferenceService agentService,
                                 ComponentOrganizationService organizationService,
                                 MinIOService minIOService,
                                 ObjectMapper objectMapper) {
        this.projectServiceClient = projectServiceClient;
        this.agentService = agentService;
        this.organizationService = organizationService;
        this.minIOService = minIOService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> enrich(UUID projectId, UUID pageId, String workflowContext) {
        PagePair pair = pagePair(projectId, pageId);
        JsonNode figmaPage = readJson(pair.figmaPage().path("figmaObjectPath").asText());
        JsonNode webPage = readJson(pair.webPage().path("webObjectPath").asText());
        List<Map<String, Object>> figmaComponents = components(projectId, pair.figmaPage().path("id").asText());
        List<Map<String, Object>> webComponents = components(projectId, pair.webPage().path("id").asText());

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("page_name", pair.pageName());
        request.put("figma_page", figmaPage);
        request.put("web_page", webPage);
        request.put("figma_components", figmaComponents);
        request.put("web_components", webComponents);
        request.put("figma_image_base64", image(pair.figmaPage().path("figmaObjectPath").asText()));
        request.put("web_image_base64", image(pair.webPage().path("webObjectPath").asText()));
        request.put("workflow_context", workflowContext == null ? "" : workflowContext);

        JsonNode result = agentService.enrichPage(request);
        organizationService.persistMappings(projectId, result.path("mappings"));

        String root = projectRoot(pair.webPage().path("webObjectPath").asText());
        String page = sanitize(pair.pageName());
        String normalizedFigmaPath = root + "/normalized/figma-pages/" + page + "/page.json";
        String normalizedWebPath = root + "/normalized/web-pages/" + page + "/page.json";
        String figmaPath = root + "/enriched/figma-pages/" + page + "/page.json";
        String webPath = root + "/enriched/web-pages/" + page + "/page.json";
        String mappingPath = root + "/enriched/mappings/" + page + ".json";
        String catalogPath = root + "/enriched/e2e/" + page + "/component-catalog.json";
        upload(normalizedFigmaPath, objectMapper.valueToTree(Map.of(
                "pageName", pair.pageName(), "source", "FIGMA", "components", figmaComponents
        )));
        upload(normalizedWebPath, objectMapper.valueToTree(Map.of(
                "pageName", pair.pageName(), "source", "WEB", "url", webPage.path("url").asText(""),
                "components", webComponents
        )));
        upload(figmaPath, result.path("figmaPage"));
        upload(webPath, result.path("webPage"));
        upload(mappingPath, result.path("mappings"));
        upload(catalogPath, result.path("e2eCatalog"));

        Map<String, Object> response = objectMapper.convertValue(result, Map.class);
        response.put("projectId", projectId);
        response.put("pageId", pair.webPage().path("id").asText());
        response.put("pageName", pair.pageName());
        response.put("artifacts", Map.of(
                "figma", figmaPath,
                "web", webPath,
                "mappings", mappingPath,
                "e2eCatalog", catalogPath,
                "normalizedFigma", normalizedFigmaPath,
                "normalizedWeb", normalizedWebPath
        ));
        return response;
    }

    public JsonNode catalog(UUID projectId, UUID pageId) {
        PagePair pair = pagePair(projectId, pageId);
        String root = projectRoot(pair.webPage().path("webObjectPath").asText());
        String path = root + "/enriched/e2e/" + sanitize(pair.pageName()) + "/component-catalog.json";
        return readJson(path);
    }

    private PagePair pagePair(UUID projectId, UUID selectedPageId) {
        JsonNode pages = getData("/api/v1/projects/" + projectId + "/pages");
        JsonNode selected = null;
        for (JsonNode page : pages) {
            if (selectedPageId.toString().equals(page.path("id").asText())) selected = page;
        }
        if (selected == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected page does not belong to this project.");
        }
        String pageName = selected.path("name").asText("page");
        String key = matchKey(pageName);
        JsonNode figma = null;
        JsonNode web = null;
        for (JsonNode page : pages) {
            if (!key.equals(matchKey(page.path("name").asText()))) continue;
            if (!page.path("figmaObjectPath").asText("").isBlank()) figma = page;
            if (!page.path("webObjectPath").asText("").isBlank()) web = page;
        }
        if (figma == null || web == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both Figma and Web extraction are required for page '" + pageName + "'.");
        }
        return new PagePair(pageName, figma, web);
    }

    private List<Map<String, Object>> components(UUID projectId, String pageId) {
        JsonNode data = getData("/api/v1/projects/" + projectId + "/components/page/" + pageId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode component : data) result.add(objectMapper.convertValue(component, Map.class));
        return result;
    }

    private JsonNode getData(String path) {
        JsonNode envelope = projectServiceClient.get().uri(path).retrieve().bodyToMono(JsonNode.class).block();
        if (envelope == null || !envelope.path("success").asBoolean(false)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Project service request failed: " + path);
        }
        return envelope.path("data");
    }

    private JsonNode readJson(String path) {
        try {
            return objectMapper.readTree(minIOService.downloadDesign(path));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read extraction artifact: " + path);
        }
    }

    private String image(String jsonPath) {
        try {
            String imagePath = jsonPath.replaceAll("/page\\.json$", "/page.png");
            return Base64.getEncoder().encodeToString(minIOService.downloadBytes(imagePath));
        } catch (Exception ignored) {
            return "";
        }
    }

    private void upload(String path, JsonNode value) {
        try {
            minIOService.uploadText(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value), "application/json");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store enriched artifact: " + path, exception);
        }
    }

    private String projectRoot(String webObjectPath) {
        int marker = webObjectPath.indexOf("/web-pages/");
        if (marker < 1) throw new IllegalArgumentException("Invalid Web artifact path: " + webObjectPath);
        return webObjectPath.substring(0, marker);
    }

    private String matchKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String sanitize(String value) {
        String result = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return result.isBlank() ? "page" : result;
    }

    private record PagePair(String pageName, JsonNode figmaPage, JsonNode webPage) {}
}
