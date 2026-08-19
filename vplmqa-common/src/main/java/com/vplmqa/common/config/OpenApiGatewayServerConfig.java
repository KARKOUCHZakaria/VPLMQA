package com.vplmqa.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Makes generated OpenAPI documents usable from the gateway Swagger UI.
 */
@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiGatewayServerConfig {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI gatewayOpenApi(@Value("${gateway.public-url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI().servers(List.of(new Server()
                .url(gatewayUrl)
                .description("Gateway")));
    }
}
