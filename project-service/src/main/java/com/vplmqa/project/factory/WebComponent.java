package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Concrete WebComponent product.
 */
public class WebComponent extends AbstractComponent {
    private String htmlId;
    private Map<String, Object> cssProperties;

    @SuppressWarnings("unchecked")
    public WebComponent(Map<String, Object> data) {
        super(data);
        this.canonicalName = (String) data.get("canonicalName");
        this.semanticRole = (String) data.get("semanticRole");
        this.htmlId = (String) data.get("htmlId");
        this.cssProperties = (Map<String, Object>) data.get("cssProperties");
    }

    public String getHtmlId() { return htmlId; }
    public Map<String, Object> getCssProperties() { return cssProperties; }
}
