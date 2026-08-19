package com.vplmqa.project.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Page response payload.
 *
 * @param id page id
 * @param projectId project id
 * @param name page name
 * @param url page url
 * @param path page path
 * @param lastScannedAt last scan time
 * @param scanStatus scan status
 * @param source extracted source
 * @param figmaObjectPath Figma artifact path
 * @param webObjectPath web artifact path
 * @param createdAt creation time
 */
public record PageResponse(
        UUID id,
        UUID projectId,
        String name,
        String url,
        String path,
        Instant lastScannedAt,
        String scanStatus,
        String source,
        String figmaObjectPath,
        String webObjectPath,
        Instant createdAt
) {
}
