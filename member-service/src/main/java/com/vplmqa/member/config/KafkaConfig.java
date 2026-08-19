package com.vplmqa.member.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka topic configuration for member service.
 */
@Configuration
public class KafkaConfig {

    /**
     * Defines the invitation.created topic.
     *
     * @return topic bean
     */
    @Bean
    public NewTopic invitationCreatedTopic() {
        return new NewTopic("invitation.created", 1, (short) 1);
    }
}
