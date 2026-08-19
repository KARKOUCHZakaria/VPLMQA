package com.figma.design.factory.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Concrete product: Web Frame
 */
@Data
@Builder
@AllArgsConstructor
public class WebFrame implements IFrame {
    private String id;
    private String containerClass;
    private Object properties;
    private Object children;

    @Override
    public String getId() {
        return id;
    }
}
