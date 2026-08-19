package com.vplmqa.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Main entry point for the Eureka server application.
 */
@SpringBootApplication(scanBasePackages = {"com.vplmqa"})
@EnableEurekaServer
public class EurekaServerApplication {

    /**
     * Bootstraps the Eureka server.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
