package com.figma.design.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "figma.api")
public class FigmaApiProperties {

    private String baseUrl = "https://api.figma.com";

    private String token;
}