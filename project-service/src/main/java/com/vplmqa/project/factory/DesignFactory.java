package com.vplmqa.project.factory;

import java.util.Map;

/**
 * Abstract factory for creating design-related objects from raw data.
 */
public abstract class DesignFactory {
    
    /**
     * Creates a page representation from raw data.
     *
     * @param data the raw page data
     * @return the instantiated abstract page
     */
    public abstract AbstractPage createPage(Map<String, Object> data);

    /**
     * Creates a component representation from raw data.
     *
     * @param data the raw component data
     * @return the instantiated abstract component
     */
    public abstract AbstractComponent createComponent(Map<String, Object> data);
}
