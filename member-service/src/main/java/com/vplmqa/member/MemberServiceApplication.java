package com.vplmqa.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main entry point for the member service.
 */
@SpringBootApplication(scanBasePackages = {"com.vplmqa"})
@EnableKafka
public class MemberServiceApplication {

    /**
     * Boots the service.
     *
     * @param args application arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }
}
