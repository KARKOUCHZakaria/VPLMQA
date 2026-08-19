package com.vplmqa.project.dto;

import com.vplmqa.project.enumtype.ComponentSourceEnum;
import com.vplmqa.project.enumtype.ComponentStatusEnum;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Component response payload.
 *
 * @param id component id
 * @param pageId page id
 * @param canonicalName canonical name
 * @param semanticRole semantic role
 * @param functionalMeaning functional meaning
 * @param htmlId html id
 * @param figmaNodeId figma node id
 * @param source source
 * @param status status
 * @param cssProperties css properties
 * @param boundingBox bounding box
 * @param screenshot screenshot url
 * @param createdAt created time
 * @param updatedAt updated time
 */
public record ComponentResponse(
        UUID id,
        UUID pageId,
        String canonicalName,
        String semanticRole,
        String functionalMeaning,
        String htmlId,
        String figmaNodeId,
        String testIdentifier,
        String cssSelector,
        String xpath,
        ComponentSourceEnum source,
        ComponentStatusEnum status,
        Map<String, Object> cssProperties,
        Map<String, Object> boundingBox,
        String screenshot,
        Instant createdAt,
        Instant updatedAt
) {
}
