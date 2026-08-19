package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Concrete WebPage product.
 */
public class WebPage extends AbstractPage {
    private String htmlContent;

    public WebPage(Map<String, Object> data) {
        super(data);
        this.name = (String) data.getOrDefault("name", "Unknown Web Page");
        this.url = (String) data.get("url");
        this.htmlContent = (String) data.get("htmlContent");
    }

    public String getHtmlContent() { return htmlContent; }
}
