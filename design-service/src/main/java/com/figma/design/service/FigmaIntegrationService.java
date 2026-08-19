package com.figma.design.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.figma.design.exception.FigmaIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Service for integrating with Figma API
 * Provides methods to fetch designs and files from Figma
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaIntegrationService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${figma.api.base-url:https://api.figma.com}")
    private String figmaBaseUrl;

    @Value("${figma.api.token:}")
    private String configuredApiToken;

    /**
     * Fetch complete design/file from Figma API
     * 
     * @param fileKey Figma file key
     * @param apiToken Optional custom API token (uses configured token if not provided)
     * @return Object containing the Figma design (can be converted to JsonNode)
     */
    public Object fetchFigmaDesign(String fileKey, String apiToken) {
        try {
            String token = resolveApiToken(apiToken);
            
            if (!StringUtils.hasText(fileKey)) {
                throw new IllegalArgumentException("fileKey is required");
            }

            log.info("Fetching Figma design for file key: {}", fileKey);

            Object response = webClientBuilder
                    .baseUrl(figmaBaseUrl)
                    .defaultHeader("X-Figma-Token", token)
                    .build()
                    .get()
                    .uri("/v1/files/{fileKey}", fileKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .block();

            if (response == null) {
                throw new FigmaIntegrationException("Figma API returned an empty response");
            }

            log.info("Successfully fetched Figma design for file key: {}", fileKey);
            return response;

        } catch (WebClientResponseException exception) {
            String errorMessage = buildFigmaErrorMessage(exception);
            log.error("Failed to fetch Figma design: {}", errorMessage);
            throw new FigmaIntegrationException(errorMessage);
        } catch (Exception e) {
            log.error("Unexpected error fetching Figma design for file key: {}", fileKey, e);
            throw new FigmaIntegrationException("Failed to fetch design from Figma: " + e.getMessage());
        }
    }

    /**
     * Resolve which API token to use
     * Prefers the provided token, falls back to configured token
     * 
     * @param providedToken Optional provided token
     * @return API token to use
     */
    private String resolveApiToken(String providedToken) {
        if (StringUtils.hasText(providedToken)) {
            return providedToken;
        }
        
        if (!StringUtils.hasText(configuredApiToken)) {
            throw new IllegalArgumentException("No Figma API token provided or configured");
        }
        
        return configuredApiToken;
    }

    /**
     * Build user-friendly error message from Figma API response
     * 
     * @param exception WebClientResponseException from Figma API
     * @return Formatted error message
     */
    private String buildFigmaErrorMessage(WebClientResponseException exception) {
        try {
            String responseBody = exception.getResponseBodyAsString();
            if (StringUtils.hasText(responseBody)) {
                JsonNode errorJson = objectMapper.readTree(responseBody);
                String status = errorJson.path("status").asText();
                String message = errorJson.path("err").asText();
                
                if (StringUtils.hasText(message)) {
                    return String.format("Figma API error [%s]: %s", status, message);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse Figma error response", e);
        }
        
        return String.format("Figma API error [%d]: %s", exception.getStatusCode().value(), exception.getMessage());
    }
}
