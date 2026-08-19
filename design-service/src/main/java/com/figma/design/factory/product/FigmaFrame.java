package com.figma.design.factory.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Concrete product: Figma Frame
 */
@Data
@Builder
@AllArgsConstructor
public class FigmaFrame implements IFrame {
    private String id;
    private Object properties;
    private Object children;

    @Override
    public String getId() {
        return id;
    }
}
