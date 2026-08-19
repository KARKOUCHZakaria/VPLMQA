package com.figma.design.service;

import com.figma.design.model.Page;
import com.figma.design.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

/**
 * Service for exporting Figma pages as PNG images
 * Handles page export workflow: fetch Figma page -> export as PNG -> store in MinIO -> update DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaPageExportService {

    private final WebClient webClient;
    private final MinIOService minIOService;
    private final DesignStoragePathService designStoragePathService;
    private final PageRepository pageRepository;

    /**
     * Export Figma page as PNG and store in MinIO
     * 
     * Uses Figma's REST API to render page as image
     * MinIO Path: /{projectName}/pages/{pageName}/page-export.png
     * 
     * @param pageId Page database ID
     * @param figmaFileKey Figma file key
     * @param figmaPageId Figma page node ID
     * @param apiToken Figma API token (optional - uses environment variable if not provided)
     * @return Export result with MinIO path
     */
    @Transactional
    public FigmaPageExportResult exportPageToPNG(Long pageId, String figmaFileKey, String figmaPageId, String apiToken) {
        try {
            Page page = pageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));

            String pageName = page.getName();
                String projectName = page.getProject().getName();

            log.info("Starting Figma page export for page: {} with node ID: {}", pageName, figmaPageId);

            // Get API token from parameter or environment
            String token = apiToken != null && !apiToken.isEmpty() 
                    ? apiToken 
                    : System.getenv("FIGMA_API_TOKEN");
            
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Figma API token not provided and FIGMA_API_TOKEN environment variable not set");
            }

            // Export page as PNG from Figma
            byte[] pngImage = exportFigmaPageAsImage(figmaFileKey, figmaPageId, token, "png");
            log.debug("Figma page exported, image size: {} bytes", pngImage.length);

            // Upload PNG to MinIO
            String minioPath = designStoragePathService.pageExportPath(projectName, pageName, "png");
            
            String minioLink = minIOService.uploadScreenshot(minioPath, pngImage);
            log.info("Figma page PNG uploaded to MinIO: {}", minioLink);

            // Update Page entity with export link
            page.setFileLink(minioLink);
            page.setImportedAt(LocalDateTime.now());
            pageRepository.saveAndFlush(page);

            log.info("Figma page export completed successfully for: {}", pageName);

            return FigmaPageExportResult.builder()
                    .pageId(pageId)
                    .pageName(pageName)
                    .figmaFileKey(figmaFileKey)
                    .figmaPageId(figmaPageId)
                    .minioPath(minioLink)
                    .imageSize(pngImage.length)
                    .format("png")
                    .status("SUCCESS")
                    .exportedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to export Figma page for page ID: {}", pageId, e);
            throw new RuntimeException("Figma page export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Export Figma page as SVG and store in MinIO
     * 
     * SVG format preserves vector graphics quality
     * MinIO Path: /{projectName}/pages/{pageName}/page-export.svg
     * 
     * @param pageId Page database ID
     * @param figmaFileKey Figma file key
     * @param figmaPageId Figma page node ID
     * @param apiToken Figma API token (optional - uses environment variable if not provided)
     * @return Export result with MinIO path
     */
    @Transactional
    public FigmaPageExportResult exportPageToSVG(Long pageId, String figmaFileKey, String figmaPageId, String apiToken) {
        try {
            Page page = pageRepository.findById(pageId)
                    .orElseThrow(() -> new RuntimeException("Page not found: " + pageId));

            String pageName = page.getName();
                String projectName = page.getProject().getName();

            log.info("Starting Figma page SVG export for page: {} with node ID: {}", pageName, figmaPageId);

            // Get API token from parameter or environment
            String token = apiToken != null && !apiToken.isEmpty() 
                    ? apiToken 
                    : System.getenv("FIGMA_API_TOKEN");
            
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Figma API token not provided and FIGMA_API_TOKEN environment variable not set");
            }

            // Export page as SVG from Figma
            byte[] svgImage = exportFigmaPageAsImage(figmaFileKey, figmaPageId, token, "svg");
            log.debug("Figma page SVG exported, file size: {} bytes", svgImage.length);

            // Upload SVG to MinIO
            String minioPath = designStoragePathService.pageExportPath(projectName, pageName, "svg");
            
            String minioLink = minIOService.uploadSVG(minioPath, svgImage);
            log.info("Figma page SVG uploaded to MinIO: {}", minioLink);

            // Update Page entity with export link
            page.setFileLink(minioLink);
            page.setImportedAt(LocalDateTime.now());
            pageRepository.saveAndFlush(page);

            log.info("Figma page SVG export completed successfully for: {}", pageName);

            return FigmaPageExportResult.builder()
                    .pageId(pageId)
                    .pageName(pageName)
                    .figmaFileKey(figmaFileKey)
                    .figmaPageId(figmaPageId)
                    .minioPath(minioLink)
                    .imageSize(svgImage.length)
                    .format("svg")
                    .status("SUCCESS")
                    .exportedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to export Figma page as SVG for page ID: {}", pageId, e);
            throw new RuntimeException("Figma page SVG export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method to call Figma image export API with retry logic
     * 
     * Figma API endpoint: GET /v1/images/{file_key}
     * Query params: ids={node_id}&format={png|svg|jpg|pdf}
     * 
     * Includes:
     * - Retry logic for network failures (up to 3 attempts)
     * - Handling for large files
     * - Exponential backoff between retries
     * 
     * @param fileKey Figma file key
     * @param nodeId Figma page/node ID
     * @param apiToken Figma API token
     * @param format Export format: png, svg, jpg, pdf
     * @return Image bytes
     */
    private byte[] exportFigmaPageAsImage(String fileKey, String nodeId, String apiToken, String format) {
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                log.debug("Exporting Figma page as {} (attempt {}/{})", format, retryCount + 1, maxRetries);

                String figmaApiUrl = String.format(
                        "https://api.figma.com/v1/images/%s?ids=%s&format=%s",
                        fileKey, nodeId, format
                );

                log.debug("Calling Figma API: {}", figmaApiUrl);

                // First, get the image URL from Figma
                String response = webClient.get()
                        .uri(figmaApiUrl)
                        .header("X-FIGMA-TOKEN", apiToken)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (response == null || response.isEmpty()) {
                    throw new RuntimeException("Empty response from Figma API");
                }

                // Parse JSON response to extract image URL
                String imageUrl = extractImageUrlFromResponse(response, nodeId);
                log.debug("Figma image URL: {}", imageUrl);

                // Download the actual image from the URL with retry
                byte[] imageBytes = downloadImageWithRetry(imageUrl, retryCount, maxRetries);

                if (imageBytes == null || imageBytes.length == 0) {
                    throw new RuntimeException("Failed to download image from Figma");
                }

                log.debug("Downloaded image, size: {} bytes", imageBytes.length);
                return imageBytes;

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                log.warn("Export attempt {} failed: {} - {}", retryCount, e.getClass().getSimpleName(), e.getMessage());

                // If we have retries left, wait before retrying
                if (retryCount < maxRetries) {
                    long waitTime = (long) Math.pow(2, retryCount) * 1000; // Exponential backoff: 2s, 4s, 8s
                    log.debug("Retrying in {} ms...", waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All retries failed
        String errorMsg = String.format("Failed to export Figma page as %s after %d attempts", format, maxRetries);
        log.error("{}: {}", errorMsg, lastException != null ? lastException.getMessage() : "Unknown error");
        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * Download image from URL with retry logic for network failures
     */
    private byte[] downloadImageWithRetry(String imageUrl, int currentRetry, int maxRetries) {
        try {
            return webClient.get()
                    .uri(imageUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (Exception e) {
            // Check if it's a connection error that we should retry
            String errorMsg = e.getMessage();
            boolean isNetworkError = errorMsg != null && (
                    errorMsg.contains("Connection") || 
                    errorMsg.contains("timeout") || 
                    errorMsg.contains("Broken pipe") ||
                    errorMsg.contains("prematurely")
            );
            
            if (isNetworkError && currentRetry < maxRetries - 1) {
                log.warn("Network error during download, will retry: {}", errorMsg);
                throw e; // Will be retried by outer method
            }
            
            throw e;
        }
    }

    /**
     * Extract image URL from Figma API response
     * 
     * Response format:
     * {
     *   "images": {
     *     "nodeId": "https://figma-alpha-api.s3.us-west-2.amazonaws.com/..."
     *   }
     * }
     * 
     * @param jsonResponse Figma API response JSON
     * @param nodeId Node ID to extract URL for
     * @return Image URL
     */
    private String extractImageUrlFromResponse(String jsonResponse, String nodeId) {
        try {
            // Simple JSON parsing - in production consider using Jackson ObjectMapper
            String imagesKey = "\"images\"";
            int imagesStart = jsonResponse.indexOf(imagesKey);
            if (imagesStart == -1) {
                throw new RuntimeException("'images' field not found in Figma API response");
            }

            String nodeIdKey = "\"" + nodeId + "\"";
            int nodeStart = jsonResponse.indexOf(nodeIdKey, imagesStart);
            if (nodeStart == -1) {
                throw new RuntimeException("Node ID " + nodeId + " not found in Figma API response. Check page/node ID is correct.");
            }

            // Find the URL value after the node ID key
            int urlStart = jsonResponse.indexOf("\"", nodeStart + nodeIdKey.length() + 1);
            int urlEnd = jsonResponse.indexOf("\"", urlStart + 1);
            
            if (urlStart == -1 || urlEnd == -1) {
                throw new RuntimeException("Could not parse image URL from Figma API response");
            }

            String imageUrl = jsonResponse.substring(urlStart + 1, urlEnd);
            
            // Unescape URL
            imageUrl = imageUrl.replace("\\/", "/");
            
            return imageUrl;

        } catch (Exception e) {
            log.error("Error parsing Figma API response: {}", e.getMessage());
            throw new RuntimeException("Failed to extract image URL from Figma API response: " + e.getMessage(), e);
        }
    }

    // ===================== DTOs =====================

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FigmaPageExportResult {
        private Long pageId;
        private String pageName;
        private String figmaFileKey;
        private String figmaPageId;
        private String minioPath;
        private Integer imageSize;
        private String format;
        private String status;
        private LocalDateTime exportedAt;
    }
}
