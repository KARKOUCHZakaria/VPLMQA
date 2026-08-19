package com.vplmqa.project.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka configuration for project events.
 */
@Configuration
public class KafkaConfig {

    /**
     * Creates the project.created topic definition for local dev.
     *
     * @return topic bean
     */
    @Bean
    public NewTopic projectCreatedTopic() {
        return new NewTopic("project.created", 1, (short) 1);
    }
}
