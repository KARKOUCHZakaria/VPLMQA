package com.figma.design.factory.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Concrete product: Figma Page
 */
@Data
@Builder
@AllArgsConstructor
public class FigmaPage implements IPage {
    private String id;
    private String name;
    private String figmaPageId;
    private String figmaUrl;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }
}
