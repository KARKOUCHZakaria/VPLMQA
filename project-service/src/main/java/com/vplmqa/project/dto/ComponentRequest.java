package com.vplmqa.project.dto;

import com.vplmqa.project.enumtype.ComponentSourceEnum;
import com.vplmqa.project.enumtype.ComponentStatusEnum;

import java.util.Map;
import java.util.UUID;

/**
 * Request payload for upserting a component.
 *
 * @param pageId page identifier
 * @param canonicalName canonical component name
 * @param semanticRole semantic role
 * @param functionalMeaning functional meaning
 * @param htmlId DOM id
 * @param figmaNodeId Figma node id
 * @param source component source
 * @param status component status
 * @param cssProperties CSS properties JSON
 * @param boundingBox bounding box JSON
 * @param screenshot screenshot URL
 */
public record ComponentRequest(
        UUID pageId,
        String canonicalName,
        String semanticRole,
        String functionalMeaning,
        String htmlId,
        String figmaNodeId,
        ComponentSourceEnum source,
        ComponentStatusEnum status,
        Map<String, Object> cssProperties,
        Map<String, Object> boundingBox,
        String screenshot
) {
}
