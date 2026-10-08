package com.vplmqa.ticket.service;

import com.vplmqa.ticket.dto.TicketRequest;
import com.vplmqa.ticket.enumtype.SeverityEnum;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TestFailedConsumer {

    private final TicketService ticketService;

    public TestFailedConsumer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @KafkaListener(topics = "test.failed", groupId = "ticket-service")
    public void onMessage(String payload) {
        ticketService.create(new TicketRequest(UUID.randomUUID(), "Automated test failure", payload, SeverityEnum.MEDIUM, null, null, null, null, null, null, List.of("Automated", "E2E")));
    }
}
