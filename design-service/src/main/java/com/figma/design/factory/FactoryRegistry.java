package com.figma.design.factory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory registry for selecting the appropriate ComponentFactory implementation
 */
@Slf4j
@Component
public class FactoryRegistry {

    private final Map<ComponentFactory.PlatformType, ComponentFactory> factories = new HashMap<>();

    public FactoryRegistry(
            FigmaComponentFactory figmaFactory,
            WebComponentFactory webFactory) {
        factories.put(ComponentFactory.PlatformType.FIGMA, figmaFactory);
        factories.put(ComponentFactory.PlatformType.WEB, webFactory);
        log.info("FactoryRegistry initialized with {} factories", factories.size());
    }

    /**
     * Get the factory for a specific platform
     */
    public ComponentFactory getFactory(ComponentFactory.PlatformType platformType) {
        ComponentFactory factory = factories.get(platformType);
        if (factory == null) {
            throw new IllegalArgumentException("No factory found for platform: " + platformType);
        }
        return factory;
    }

    /**
     * Get the Figma factory
     */
    public FigmaComponentFactory getFigmaFactory() {
        return (FigmaComponentFactory) getFactory(ComponentFactory.PlatformType.FIGMA);
    }

    /**
     * Get the Web factory
     */
    public WebComponentFactory getWebFactory() {
        return (WebComponentFactory) getFactory(ComponentFactory.PlatformType.WEB);
    }
}
