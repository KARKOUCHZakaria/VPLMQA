package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.dto.MlPredictionBatchResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DesignTokenMlInferenceService {

    private final ObjectMapper objectMapper;
    private final RestClient agentClient;

    public DesignTokenMlInferenceService(
            ObjectMapper objectMapper,
            @Value("${part1.agent.base-url:http://langgraph-agents:8090}") String agentBaseUrl) {
        this.objectMapper = objectMapper;
        this.agentClient = RestClient.builder()
                .baseUrl(agentBaseUrl.replaceAll("/+$", ""))
                .build();
    }

    public MlPredictionBatchResponse predict(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("At least one design-token comparison row is required.");
        }
        JsonNode response = post("/api/v1/agents/part1/run", Map.of("page_name", "token-comparison", "rows", rows));
        return objectMapper.convertValue(response, MlPredictionBatchResponse.class);
    }

    public Map<String, Object> status() {
        return objectMapper.convertValue(get("/api/v1/agents/part1/model/status"), Map.class);
    }

    public JsonNode runComparison(Map<String, Object> request) {
        return post("/api/v1/agents/part1/run", request);
    }

    public JsonNode organizeComponents(Map<String, Object> request) {
        return post("/api/v1/agents/part1/organize-components", request);
    }

    public JsonNode enrichPage(Map<String, Object> request) {
        return post("/api/v1/agents/part1/enrich-page", request);
    }

    private JsonNode get(String path) {
        try {
            JsonNode response = agentClient.get().uri(path).retrieve().body(JsonNode.class);
            if (response == null) throw new IllegalStateException("Part 1 agent returned an empty response.");
            return response;
        } catch (RestClientResponseException exception) {
            throw agentError(exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Part 1 agent request failed: " + exception.getMessage(), exception);
        }
    }

    private JsonNode post(String path, Object body) {
        try {
            JsonNode response = agentClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) throw new IllegalStateException("Part 1 agent returned an empty response.");
            return response;
        } catch (RestClientResponseException exception) {
            throw agentError(exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Part 1 agent request failed: " + exception.getMessage(), exception);
        }
    }

    private IllegalStateException agentError(RestClientResponseException exception) {
        return new IllegalStateException(
                "Part 1 agent returned HTTP " + exception.getStatusCode().value() + ": " + exception.getResponseBodyAsString(),
                exception
        );
    }
}
