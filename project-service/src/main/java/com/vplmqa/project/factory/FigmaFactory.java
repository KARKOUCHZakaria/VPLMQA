package com.vplmqa.project.factory;

import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Concrete factory for Figma sources.
 */
@Component
public class FigmaFactory extends DesignFactory {

    @Override
    public AbstractPage createPage(Map<String, Object> data) {
        return new FigmaPage(data);
    }

    @Override
    public AbstractComponent createComponent(Map<String, Object> data) {
        return new FigmaComponent(data);
    }
}
