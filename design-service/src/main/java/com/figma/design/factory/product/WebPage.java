package com.figma.design.factory.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Concrete product: Web Page
 */
@Data
@Builder
@AllArgsConstructor
public class WebPage implements IPage {
    private String id;
    private String name;
    private String url;
    private String tag;
    private String figmaPageId;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }
}
