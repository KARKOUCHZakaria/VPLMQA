package com.figma.design.service;

import com.figma.design.exception.FigmaIntegrationException;
import com.figma.design.util.FileNameSanitizer;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinIOService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:figma-designs}")
    private String bucketName;

    /**
     * Upload design file to MinIO
     * @param fileName Figma file name or full path (e.g., "pfe/pages/dashbord/page.json")
     * @param jsonContent Design content as JSON string
     * @return MinIO object path
     */
    public String uploadDesign(String fileName, String jsonContent) {
        try {
            // Create bucket if it doesn't exist
            ensureBucketExists();

            String objectName;
            
            // Check if fileName contains path separators (hierarchical path)
            if (fileName.contains("/") || fileName.contains("\\")) {
                // Use the full path as-is (already has desired structure)
                objectName = fileName;
                log.debug("Using full hierarchical path: {}", objectName);
            } else {
                // Legacy behavior: sanitize and add designs prefix for simple filenames
                String sanitizedName = FileNameSanitizer.sanitize(fileName);
                objectName = FileNameSanitizer.generateFileNameWithExtension("designs", "json", sanitizedName);
                log.debug("Using legacy path with designs prefix: {}", objectName);
            }

            // Convert JSON string to InputStream
            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(jsonBytes);

            // Upload to MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, jsonBytes.length, -1)
                            .contentType("application/json")
                            .build()
            );

            log.info("Design file uploaded to MinIO: {}/{}", bucketName, objectName);
            return objectName;

        } catch (Exception exception) {
            log.error("Failed to upload design to MinIO", exception);
            throw new FigmaIntegrationException("Failed to upload design to MinIO: " + exception.getMessage());
        }
    }

    public String uploadText(String objectName, String content, String contentType) {
        try {
            ensureBucketExists();

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            InputStream inputStream = new ByteArrayInputStream(bytes);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, bytes.length, -1)
                            .contentType(contentType)
                            .build()
            );

            log.info("Text artifact uploaded to MinIO: {}/{}", bucketName, objectName);
            return objectName;
        } catch (Exception exception) {
            log.error("Failed to upload text artifact to MinIO", exception);
            throw new FigmaIntegrationException("Failed to upload text artifact to MinIO: " + exception.getMessage());
        }
    }

    /**
     * Download/fetch design file content from MinIO
     * Handles both new hierarchical paths and old flat paths for backwards compatibility
     * 
     * @param objectPath MinIO object path (returned from uploadDesign)
     * @return JSON content as string
     */
    public String downloadDesign(String objectPath) {
        try {
            // Try to download from the exact path first
            return downloadFromPath(objectPath);
            
        } catch (Exception exception) {
            log.warn("Failed to download from primary path: {}. Trying fallback paths...", objectPath);
            
            // Try alternative paths for backwards compatibility
            try {
                // If it's a hierarchical path (contains /), try old designs folder format
                if (objectPath.contains("/")) {
                    // Extract the sanitized name and try designs folder
                    String fileName = objectPath.substring(objectPath.lastIndexOf("/") + 1);
                    String fallbackPath = "designs/" + fileName;
                    log.debug("Trying fallback path: {}", fallbackPath);
                    return downloadFromPath(fallbackPath);
                }
            } catch (Exception fallbackException) {
                log.debug("Fallback path also failed");
            }
            
            // If we get here, both paths failed
            log.error("Failed to download design from MinIO with any path: {}", objectPath, exception);
            throw new FigmaIntegrationException("Failed to download design from MinIO: " + exception.getMessage());
        }
    }

    /**
     * Helper method to download from a specific path
     */
    private String downloadFromPath(String objectPath) throws Exception {
        InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectPath)
                        .build()
        );

        byte[] bytes = stream.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        
        log.info("Design file downloaded from MinIO: {}/{}", bucketName, objectPath);
        return content;
    }

    public byte[] downloadBytes(String objectPath) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(objectPath).build())) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            throw new FigmaIntegrationException("Failed to download artifact from MinIO: " + objectPath);
        }
    }

    private void ensureBucketExists() {
        try {
            if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            }
        } catch (Exception exception) {
            log.error("Failed to ensure bucket exists", exception);
            throw new FigmaIntegrationException("Failed to ensure MinIO bucket exists: " + exception.getMessage());
        }
    }

    /**
     * Upload screenshot to MinIO
     * @param objectName Full path (e.g., "project-name/web-pages/page-name/screenshot.png")
     * @param screenshotBytes Screenshot image bytes
     * @return MinIO object path
     */
    public String uploadScreenshot(String objectName, byte[] screenshotBytes) {
        try {
            ensureBucketExists();

            InputStream inputStream = new ByteArrayInputStream(screenshotBytes);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, screenshotBytes.length, -1)
                            .contentType("image/png")
                            .build()
            );

            log.info("Screenshot uploaded to MinIO: {}/{}", bucketName, objectName);
            return objectName;

        } catch (Exception exception) {
            log.error("Failed to upload screenshot to MinIO", exception);
            throw new FigmaIntegrationException("Failed to upload screenshot to MinIO: " + exception.getMessage());
        }
    }

    /**
     * Upload SVG file to MinIO
     * @param objectName Full path (e.g., "project-name/pages/page-name/page-export.svg")
     * @param svgBytes SVG file bytes
     * @return MinIO object path
     */
    public String uploadSVG(String objectName, byte[] svgBytes) {
        try {
            ensureBucketExists();

            InputStream inputStream = new ByteArrayInputStream(svgBytes);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, svgBytes.length, -1)
                            .contentType("image/svg+xml")
                            .build()
            );

            log.info("SVG file uploaded to MinIO: {}/{}", bucketName, objectName);
            return objectName;

        } catch (Exception exception) {
            log.error("Failed to upload SVG file to MinIO", exception);
            throw new FigmaIntegrationException("Failed to upload SVG file to MinIO: " + exception.getMessage());
        }
    }
}

