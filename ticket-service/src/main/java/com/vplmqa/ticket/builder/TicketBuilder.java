package com.vplmqa.ticket.builder;

import com.vplmqa.ticket.dto.ComparisonResultDto;
import com.vplmqa.ticket.dto.TestExecutionDto;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.enumtype.SeverityEnum;
import com.vplmqa.ticket.enumtype.TicketStatusEnum;

import java.util.UUID;

/**
 * Fluent builder for assembling Ticket entities from heterogeneous event sources.
 */
public class TicketBuilder {
    private String title;
    private SeverityEnum severity;
    private UUID comparisonResultId;
    private UUID testExecutionId;
    private String componentCanonicalName;
    private String componentHtmlId;
    private UUID projectId;

    public TicketBuilder withTitle(String title) { 
        this.title = title; 
        return this; 
    }

    public TicketBuilder withSeverity(SeverityEnum s) { 
        this.severity = s; 
        return this; 
    }

    public TicketBuilder fromTestExecution(TestExecutionDto exec) { 
        this.testExecutionId = exec.id();
        this.projectId = exec.projectId();
        this.componentCanonicalName = exec.componentCanonicalName();
        this.componentHtmlId = exec.componentHtmlId();
        
        if ("ERROR".equalsIgnoreCase(exec.status()) || "FAILED".equalsIgnoreCase(exec.status())) {
            this.severity = SeverityEnum.CRITICAL;
        }
        return this; 
    }

    public TicketBuilder fromComparison(ComparisonResultDto comp) { 
        this.comparisonResultId = comp.id();
        this.projectId = comp.projectId();
        this.componentCanonicalName = comp.componentCanonicalName();
        this.componentHtmlId = comp.componentHtmlId();
        
        if (comp.confidence() < 0.5f) {
            this.severity = SeverityEnum.HIGH;
        } else {
            this.severity = SeverityEnum.MEDIUM;
        }
        return this; 
    }

    public Ticket build() { 
        if (title == null || title.isBlank()) {
            throw new IllegalStateException("Title is required");
        }
        if (projectId == null) {
            throw new IllegalStateException("Project ID is required");
        }
        
        Ticket ticket = new Ticket();
        ticket.setTitle(this.title);
        ticket.setDescription("Ticket for " + componentCanonicalName + " (#" + componentHtmlId + ")");
        ticket.setSeverity(this.severity != null ? this.severity : SeverityEnum.LOW);
        ticket.setStatus(TicketStatusEnum.OPEN);
        ticket.setProjectId(this.projectId);
        
        // Use standard setters from Ticket entity if needed. 
        // For simplicity, these custom fields might map to the description or specific columns.
        return ticket;
    }
}
