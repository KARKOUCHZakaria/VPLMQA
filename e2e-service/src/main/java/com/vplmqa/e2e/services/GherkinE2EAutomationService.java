package com.vplmqa.e2e.services;

import com.vplmqa.e2e.dto.GherkinE2ERunRequest;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

@Service
public class GherkinE2EAutomationService {

    private static final Logger log = LoggerFactory.getLogger(GherkinE2EAutomationService.class);
    private final String agentsUrl;
    private final RestClient restClient;
    private final RestClient projectClient;
    private final RestClient designClient;
    private final Duration agentReadTimeout;

    public GherkinE2EAutomationService(
            @Value("${langgraph.agents.url:http://localhost:8090}") String agentsUrl,
            @Value("${langgraph.agents.connect-timeout-ms:5000}") long agentConnectTimeoutMs,
            @Value("${langgraph.agents.read-timeout-ms:1200000}") long agentReadTimeoutMs,
            @Value("${project.service.url:http://project-service:8088}") String projectServiceUrl,
            @Value("${design.service.url:http://design-service:8082}") String designServiceUrl) {
        this.agentsUrl = agentsUrl;
        this.agentReadTimeout = Duration.ofMillis(agentReadTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(agentsUrl)
                .requestFactory(agentRequestFactory(agentConnectTimeoutMs, agentReadTimeoutMs))
                .build();
        this.projectClient = RestClient.builder().baseUrl(projectServiceUrl).build();
        this.designClient = RestClient.builder().baseUrl(designServiceUrl).build();
        log.info("LangGraph E2E agent URL configured as {} with read timeout {} ms", agentsUrl, agentReadTimeoutMs);
    }

    public Map<String, Object> run(GherkinE2ERunRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gherkin", request.gherkin());
        payload.put("headless", request.headless() == null || request.headless());
        payload.put("repair_on_failure", request.repairOnFailure() == null || request.repairOnFailure());
        payload.put("max_repair_attempts", request.maxRepairAttempts() == null ? 1 : request.maxRepairAttempts());
        payload.put("execute", request.execute() == null || request.execute());
        payload.put("component_catalog", loadComponentCatalog(request));
        payload.put("project_context", loadProjectContext(request));

        List<String> attempted = new ArrayList<>();
        RestClient selectedAgentClient = selectReachableAgentClient(attempted);
        try {
            return postToAgent(selectedAgentClient, payload);
        } catch (RestClientResponseException exception) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "failed");
            result.put("success", false);
            result.put("steps_executed", 0);
            result.put("message", "LangGraph agent returned HTTP " + exception.getStatusCode().value());
            result.put("error", exception.getResponseBodyAsString());
            result.put("attempted_agent_urls", attempted);
            return result;
        } catch (RestClientException exception) {
            log.warn("LangGraph E2E agent request failed after waiting up to {}: {}",
                    agentReadTimeout, exception.getMessage(), exception);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "failed");
            result.put("success", false);
            result.put("steps_executed", 0);
            result.put("message", "LangGraph agent request failed");
            result.put("error", exception.getMessage());
            result.put("attempted_agent_urls", attempted);
            return result;
        }
    }

    private Map<String, Object> postToAgent(RestClient client, Map<String, Object> payload) {
        return client.post()
            .uri("/gherkin/e2e/run")
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    private RestClient selectReachableAgentClient(List<String> attempted) {
        List<String> candidates = new ArrayList<>();
        candidates.add(agentsUrl);
        for (String fallbackUrl : List.of("http://localhost:8090", "http://host.docker.internal:8090")) {
            if (candidates.stream().noneMatch(existing -> existing.equalsIgnoreCase(fallbackUrl))) {
                candidates.add(fallbackUrl);
            }
        }

        for (String candidate : candidates) {
            attempted.add(candidate);
            if (isTcpReachable(candidate)) {
                if (!candidate.equalsIgnoreCase(agentsUrl)) {
                    log.warn("LangGraph E2E agent configured URL {} is not reachable. Using {}", agentsUrl, candidate);
                }
                return candidate.equalsIgnoreCase(agentsUrl)
                        ? restClient
                        : RestClient.builder()
                                .baseUrl(candidate)
                                .requestFactory(agentRequestFactory(5000, agentReadTimeout.toMillis()))
                                .build();
            }
        }

        log.warn("No LangGraph E2E agent URL passed the TCP preflight. Sending one request to configured URL {}", agentsUrl);
        return restClient;
    }

    private SimpleClientHttpRequestFactory agentRequestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    private boolean isTcpReachable(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || port < 0) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1200);
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Object loadComponentCatalog(GherkinE2ERunRequest request) {
        if (request.projectId() == null) {
            return java.util.List.of();
        }
        if (request.pageId() != null) {
            try {
                Object catalog = designClient.get()
                        .uri("/api/v1/design-token-comparison/projects/{projectId}/enrichment/catalog?pageId={pageId}",
                                request.projectId(), request.pageId())
                        .retrieve()
                        .body(Object.class);
                if (catalog instanceof java.util.List<?>) return catalog;
            } catch (Exception ignored) {
                // Fall through to the complete project catalog.
            }
        }
        try {
            Map<String, Object> envelope = projectClient.get()
                    .uri("/api/v1/projects/{projectId}/components", request.projectId())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            Object data = envelope == null ? null : envelope.get("data");
            return data instanceof java.util.List<?> ? data : java.util.List.of();
        } catch (Exception ignored) {
            return java.util.List.of();
        }
    }

    private Map<String, Object> loadProjectContext(GherkinE2ERunRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (request.projectId() == null) {
            context.put("targetMode", "EXTERNAL");
            return context;
        }
        context.put("targetMode", "PROJECT");
        context.put("projectId", request.projectId().toString());
        if (request.pageId() != null) context.put("defaultPageId", request.pageId().toString());
        try {
            Map<String, Object> projectEnvelope = projectClient.get()
                    .uri("/api/v1/projects/{projectId}", request.projectId())
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            Object project = projectEnvelope == null ? null : projectEnvelope.get("data");
            if (project instanceof Map<?, ?> projectMap) context.put("project", projectMap);

            Map<String, Object> pagesEnvelope = projectClient.get()
                    .uri("/api/v1/projects/{projectId}/pages", request.projectId())
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            Object pages = pagesEnvelope == null ? null : pagesEnvelope.get("data");
            context.put("pages", pages instanceof java.util.List<?> ? pages : java.util.List.of());

            Map<String, Object> settingsEnvelope = projectClient.get()
                    .uri("/api/v1/projects/{projectId}/settings", request.projectId())
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            Object settings = settingsEnvelope == null ? null : settingsEnvelope.get("data");
            if (settings instanceof Map<?, ?> settingsMap) context.put("settings", settingsMap);
        } catch (Exception exception) {
            context.put("contextError", exception.getMessage());
            context.put("pages", java.util.List.of());
        }
        return context;
    }
}
