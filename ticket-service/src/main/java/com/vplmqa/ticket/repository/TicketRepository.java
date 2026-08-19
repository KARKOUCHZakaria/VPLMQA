package com.vplmqa.ticket.repository;

import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.enumtype.SeverityEnum;
import com.vplmqa.ticket.enumtype.TicketStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByProjectId(UUID projectId);
    List<Ticket> findByProjectIdAndStatus(UUID projectId, TicketStatusEnum status);
    List<Ticket> findByProjectIdAndSeverity(UUID projectId, SeverityEnum severity);
}
