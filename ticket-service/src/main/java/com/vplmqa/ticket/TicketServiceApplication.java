package com.vplmqa.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main entry point for the ticket service.
 */
@SpringBootApplication(scanBasePackages = {"com.vplmqa"})
@EnableKafka
public class TicketServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
