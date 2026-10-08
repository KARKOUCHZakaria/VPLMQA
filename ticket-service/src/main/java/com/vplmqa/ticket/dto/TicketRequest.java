package com.vplmqa.ticket.dto;

import com.vplmqa.ticket.enumtype.SeverityEnum;
import com.vplmqa.ticket.enumtype.TicketStatusEnum;
import java.util.List;
import java.util.UUID;

public record TicketRequest(
        UUID projectId,
        String title,
        String description,
        SeverityEnum severity,
        TicketStatusEnum status,
        UUID comparisonResultId,
        UUID testExecutionId,
        String componentCanonicalName,
        String componentHtmlId,
        String assignedTo,
        List<String> tags
) {
}
