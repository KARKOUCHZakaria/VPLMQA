package com.vplmqa.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the project service.
 */
@SpringBootApplication(scanBasePackages = {"com.vplmqa"})
@EnableKafka
@EnableAsync
public class ProjectServiceApplication {

    /**
     * Bootstraps the application.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}
