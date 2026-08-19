package com.vplmqa.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Main entry point for the Spring Cloud Config Server.
 */
@SpringBootApplication(scanBasePackages = {"com.vplmqa"})
@EnableConfigServer
public class ConfigServiceApplication {

    /**
     * Bootstraps the config server.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
