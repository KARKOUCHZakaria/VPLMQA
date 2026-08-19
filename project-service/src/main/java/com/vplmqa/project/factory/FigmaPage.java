package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Concrete FigmaPage product.
 */
public class FigmaPage extends AbstractPage {
    private String figmaFileId;
    private String figmaNodeId;

    public FigmaPage(Map<String, Object> data) {
        super(data);
        this.name = (String) data.getOrDefault("name", "Unknown Figma Page");
        this.url = (String) data.get("url");
        this.figmaFileId = (String) data.get("figmaFileId");
        this.figmaNodeId = (String) data.get("figmaNodeId");
    }

    public String getFigmaFileId() { return figmaFileId; }
    public String getFigmaNodeId() { return figmaNodeId; }
}
