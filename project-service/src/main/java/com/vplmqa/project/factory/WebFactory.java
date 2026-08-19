package com.vplmqa.project.factory;

import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Concrete factory for Web sources.
 */
@Component
public class WebFactory extends DesignFactory {

    @Override
    public AbstractPage createPage(Map<String, Object> data) {
        return new WebPage(data);
    }

    @Override
    public AbstractComponent createComponent(Map<String, Object> data) {
        return new WebComponent(data);
    }
}
