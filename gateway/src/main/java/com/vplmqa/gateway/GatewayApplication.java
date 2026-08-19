package com.vplmqa.gateway;

import com.vplmqa.gateway.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.WebFilter;
import java.net.URI;

/**
 * Main entry point for the API gateway.
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
})
@EnableConfigurationProperties(JwtProperties.class)
public class GatewayApplication {

    /**
     * Bootstraps the gateway application.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Redirects Swagger UI requests to the reactive webjars endpoint.
     *
     * @return the web filter
     */
    @Bean
    public WebFilter swaggerUiRedirectFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (path.equals("/swagger-ui/index.html") || path.equals("/swagger-ui") || path.equals("/swagger-ui/")) {
                exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(URI.create("/webjars/swagger-ui/index.html"));
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }}
