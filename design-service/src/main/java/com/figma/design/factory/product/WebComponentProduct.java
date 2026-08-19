package com.figma.design.factory.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Concrete product: Web Component
 */
@Data
@Builder
@AllArgsConstructor
public class WebComponentProduct implements IComponent {
    private String id;
    private String name;
    private String componentPath;
    private Object properties;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }
}
