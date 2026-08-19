package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Abstract Component product.
 */
public abstract class AbstractComponent {
    protected String canonicalName;
    protected String semanticRole;
    
    public AbstractComponent(Map<String, Object> data) {
        // base initialization
    }
    
    public String getCanonicalName() { return canonicalName; }
    public String getSemanticRole() { return semanticRole; }
}
