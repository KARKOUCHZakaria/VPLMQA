package com.figma.design.factory;

import com.figma.design.dto.factory.PageCreationDto;
import com.figma.design.dto.factory.ComponentCreationDto;
import com.figma.design.dto.factory.FrameCreationDto;
import com.figma.design.factory.product.IPage;
import com.figma.design.factory.product.IComponent;
import com.figma.design.factory.product.IFrame;

/**
 * Abstract Factory interface for creating platform-specific components
 * Supports creating Figma pages/components and Web pages/components
 * 
 * This is the core Abstract Factory pattern implementation.
 * Each concrete factory (FigmaComponentFactory, WebComponentFactory) 
 * creates a family of related products (Pages, Components, Frames).
 */
public interface ComponentFactory {

    /**
     * Creates a platform-specific page from metadata
     */
    IPage createPage(PageCreationDto pageDto);

    /**
     * Creates a platform-specific component from configuration
     */
    IComponent createComponent(ComponentCreationDto componentDto);

    /**
     * Creates a platform-specific frame from frame data
     */
    IFrame createFrame(FrameCreationDto frameDto);

    /**
     * Get the factory type
     */
    PlatformType getPlatformType();

    enum PlatformType {
        FIGMA, WEB
    }
}
