package com.figma.design.factory;

import com.figma.design.dto.factory.PageCreationDto;
import com.figma.design.dto.factory.ComponentCreationDto;
import com.figma.design.dto.factory.FrameCreationDto;
import com.figma.design.factory.product.FigmaPage;
import com.figma.design.factory.product.FigmaComponentProduct;
import com.figma.design.factory.product.FigmaFrame;
import com.figma.design.factory.product.IPage;
import com.figma.design.factory.product.IComponent;
import com.figma.design.factory.product.IFrame;
import com.figma.design.model.FigmaComponent;
import com.figma.design.model.ComponentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Concrete factory for creating Figma-specific components
 * 
 * This factory implements ComponentFactory to create Figma page, component, 
 * and frame products. All products implement the abstract product interfaces
 * (IPage, IComponent, IFrame).
 */
@Slf4j
@Component
public class FigmaComponentFactory implements ComponentFactory {

    @Override
    public IPage createPage(PageCreationDto pageDto) {
        log.info("Creating Figma page: {}", pageDto.getName());
        
        return FigmaPage.builder()
                .id("figma_" + pageDto.getName())
                .name(pageDto.getName())
                .figmaPageId(pageDto.getFigmaPageId())
                .figmaUrl(pageDto.getFigmaUrl())
                .build();
    }

    @Override
    public IComponent createComponent(ComponentCreationDto componentDto) {
        log.info("Creating Figma component: {}", componentDto.getName());
        
        return FigmaComponentProduct.builder()
                .id("figma_comp_" + componentDto.getName())
                .name(componentDto.getName())
                .figmaNodeId(componentDto.getNodeId())
                .figmaNodeType(componentDto.getNodeType())
                .build();
    }

    @Override
    public IFrame createFrame(FrameCreationDto frameDto) {
        log.info("Creating Figma frame: {}", frameDto.getId());
        
        return FigmaFrame.builder()
                .id(frameDto.getId())
                .properties(frameDto.getProperties())
                .children(frameDto.getChildren())
                .build();
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.FIGMA;
    }

    /**
     * Convert Figma component product to database entity for persistence
     */
    public FigmaComponent toEntity(FigmaComponentProduct product) {
        FigmaComponent entity = new FigmaComponent();
        entity.setType(ComponentType.FIGMA);
        entity.setFigmaNodeId(product.getFigmaNodeId());
        entity.setFigmaNodeName(product.getName());
        entity.setFigmaNodeType(product.getFigmaNodeType());
        entity.setPositionX(product.getPositionX());
        entity.setPositionY(product.getPositionY());
        entity.setNodeWidth(product.getNodeWidth());
        entity.setNodeHeight(product.getNodeHeight());
        return entity;
    }
}
