package com.figma.design.factory.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Concrete product: Figma Component
 */
@Data
@Builder
@AllArgsConstructor
public class FigmaComponentProduct implements IComponent {
    private String id;
    private String name;
    private String figmaNodeId;
    private String figmaNodeType;
    private Double positionX;
    private Double positionY;
    private Double nodeWidth;
    private Double nodeHeight;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }
}
