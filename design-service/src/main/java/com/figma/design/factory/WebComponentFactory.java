package com.figma.design.factory;

import com.figma.design.dto.factory.PageCreationDto;
import com.figma.design.dto.factory.ComponentCreationDto;
import com.figma.design.dto.factory.FrameCreationDto;
import com.figma.design.factory.product.WebPage;
import com.figma.design.factory.product.WebComponentProduct;
import com.figma.design.factory.product.WebFrame;
import com.figma.design.factory.product.IPage;
import com.figma.design.factory.product.IComponent;
import com.figma.design.factory.product.IFrame;
import com.figma.design.model.WebComponent;
import com.figma.design.model.ComponentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Concrete factory for creating Web-specific components
 * 
 * This factory implements ComponentFactory to create Web page, component,
 * and frame products. All products implement the abstract product interfaces
 * (IPage, IComponent, IFrame).
 */
@Slf4j
@Component
public class WebComponentFactory implements ComponentFactory {

    @Override
    public IPage createPage(PageCreationDto pageDto) {
        log.info("Creating web page: {}", pageDto.getName());
        
        String url = pageDto.getUrl() != null ? 
            pageDto.getUrl() : 
            "/pages/" + pageDto.getProjectName() + "/" + 
            pageDto.getName().toLowerCase().replace(" ", "-");
        
        return WebPage.builder()
                .id("web_" + pageDto.getName())
                .name(pageDto.getName())
                .url(url)
                .tag(pageDto.getTag() != null ? 
                    pageDto.getTag() : 
                    pageDto.getProjectName() + "-page-" + pageDto.getName())
                .figmaPageId(pageDto.getFigmaPageId())
                .build();
    }

    @Override
    public IComponent createComponent(ComponentCreationDto componentDto) {
        log.info("Creating web component: {}", componentDto.getName());
        
        return WebComponentProduct.builder()
                .id("web_comp_" + componentDto.getName())
                .name(componentDto.getName())
                .componentPath("src/app/components/" + 
                    componentDto.getName().replaceAll("[^a-zA-Z0-9]", ""))
                .properties(componentDto.getProperties())
                .build();
    }

    @Override
    public IFrame createFrame(FrameCreationDto frameDto) {
        log.info("Creating web frame: {}", frameDto.getId());
        
        return WebFrame.builder()
                .id(frameDto.getId())
                .containerClass("frame_" + frameDto.getId())
                .properties(mapFigmaPropsToCSS(frameDto.getProperties()))
                .children(frameDto.getChildren())
                .build();
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.WEB;
    }

    /**
     * Convert Figma properties to CSS/Tailwind classes
     */
    private Object mapFigmaPropsToCSS(Object figmaProps) {
        // TODO: Implement Figma to CSS/Tailwind conversion
        return figmaProps;
    }

    /**
     * Convert Web component product to database entity for persistence
     */
    public WebComponent toEntity(WebComponentProduct product) {
        WebComponent entity = new WebComponent();
        entity.setType(ComponentType.WEB);
        entity.setHtmlID(product.getName().replaceAll("[^a-zA-Z0-9]", ""));
        return entity;
    }
}
