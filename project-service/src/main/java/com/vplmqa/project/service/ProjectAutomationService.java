package com.vplmqa.project.service;

import com.vplmqa.project.entity.Page;
import com.vplmqa.project.repository.PageRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProjectAutomationService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAutomationService.class);
    private final ProjectExtractionService extractionService;
    private final PageRepository pageRepository;
    private final HttpClient httpClient;
    private final String designServiceUrl;

    public ProjectAutomationService(ProjectExtractionService extractionService,
                                    PageRepository pageRepository,
                                    @Value("${design.service.url:http://design-service:8082}") String designServiceUrl) {
        this.extractionService = extractionService;
        this.pageRepository = pageRepository;
        this.designServiceUrl = designServiceUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Async
    public void startProjectExtraction(UUID projectId) {
        try {
            extractionService.extractFigmaDesign(projectId);
            List<Page> pages = pageRepository.findByProjectId(projectId);
            for (Page page : pages) {
                if (page.getFigmaObjectPath() != null && !page.getFigmaObjectPath().isBlank()) {
                    try {
                        extractionService.extractFigmaComponents(projectId, page.getId());
                    } catch (Exception exception) {
                        log.warn("Automatic Figma component extraction failed for page {}: {}", page.getId(), exception.getMessage());
                    }
                }
            }
            // A Web page may have finished while Figma was still being extracted.
            // Trigger those comparisons from this side as well so ordering does not matter.
            for (Page page : pageRepository.findByProjectId(projectId)) {
                if (isWebPage(page) && hasMatchingFigmaPage(page, projectId)) {
                    compareWithRetry(projectId, page.getId());
                }
            }
        } catch (Exception exception) {
            log.error("Automatic Figma extraction failed for project {}: {}", projectId, exception.getMessage());
        }
    }

    @Async
    public void startWebPageWorkflow(UUID projectId, UUID pageId) {
        updateStatus(pageId, "WEB_EXTRACTION_RUNNING");
        try {
            extractionService.extractWebPage(projectId, pageId);
        } catch (Exception exception) {
            updateStatus(pageId, "WEB_EXTRACTION_FAILED");
            log.error("Automatic Web extraction failed for page {}: {}", pageId, exception.getMessage());
            return;
        }
        compareWithRetry(projectId, pageId);
    }

    private void compareWithRetry(UUID projectId, UUID pageId) {
        updateStatus(pageId, "COMPARISON_WAITING");
        for (int attempt = 1; attempt <= 12; attempt++) {
            try {
                updateStatus(pageId, "COMPARISON_RUNNING");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(designServiceUrl + "/api/v1/design-token-comparison/projects/" + projectId
                                + "/compare?pageId=" + pageId + "&refresh=true"))
                        .timeout(Duration.ofSeconds(120))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    updateStatus(pageId, "COMPARISON_READY");
                    return;
                }
                log.info("Comparison attempt {} for page {} returned HTTP {}", attempt, pageId, response.statusCode());
            } catch (Exception exception) {
                log.info("Comparison attempt {} for page {} is waiting: {}", attempt, pageId, exception.getMessage());
            }
            try {
                updateStatus(pageId, "COMPARISON_WAITING");
                Thread.sleep(5000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        updateStatus(pageId, "COMPARISON_FAILED");
        log.warn("Automatic comparison did not complete for page {} after retries", pageId);
    }

    private boolean isWebPage(Page page) {
        return page.getUrl() != null && !page.getUrl().isBlank()
                && page.getWebObjectPath() != null && !page.getWebObjectPath().isBlank();
    }

    private boolean hasMatchingFigmaPage(Page webPage, UUID projectId) {
        String webKey = matchKey(webPage.getName());
        return pageRepository.findByProjectId(projectId).stream()
                .anyMatch(page -> page.getFigmaObjectPath() != null
                        && !page.getFigmaObjectPath().isBlank()
                        && matchKey(page.getName()).equals(webKey));
    }

    private String matchKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private void updateStatus(UUID pageId, String status) {
        pageRepository.findById(pageId).ifPresent(page -> {
            page.setScanStatus(status);
            page.setLastScannedAt(Instant.now());
            pageRepository.save(page);
        });
    }
}
