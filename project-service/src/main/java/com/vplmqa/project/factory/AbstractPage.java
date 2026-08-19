package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Abstract Page product.
 */
public abstract class AbstractPage {
    protected String name;
    protected String url;
    
    public AbstractPage(Map<String, Object> data) {
        // base initialization
    }
    
    public String getName() { return name; }
    public String getUrl() { return url; }
}
