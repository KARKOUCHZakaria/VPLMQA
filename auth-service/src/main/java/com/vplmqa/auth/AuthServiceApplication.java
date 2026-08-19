package com.vplmqa.auth;

import com.vplmqa.auth.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the authentication service.
 */
@SpringBootApplication(scanBasePackages = {"com.vplmqa"})
@EnableConfigurationProperties(JwtProperties.class)
public class AuthServiceApplication {

    /**
     * Bootstraps the auth service.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
