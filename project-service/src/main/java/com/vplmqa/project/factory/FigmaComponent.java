package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Concrete FigmaComponent product.
 */
public class FigmaComponent extends AbstractComponent {
    private String figmaNodeId;
    private Map<String, Object> boundingBox;

    @SuppressWarnings("unchecked")
    public FigmaComponent(Map<String, Object> data) {
        super(data);
        this.canonicalName = (String) data.get("canonicalName");
        this.semanticRole = (String) data.get("semanticRole");
        this.figmaNodeId = (String) data.get("figmaNodeId");
        this.boundingBox = (Map<String, Object>) data.get("boundingBox");
    }

    public String getFigmaNodeId() { return figmaNodeId; }
    public Map<String, Object> getBoundingBox() { return boundingBox; }
}
