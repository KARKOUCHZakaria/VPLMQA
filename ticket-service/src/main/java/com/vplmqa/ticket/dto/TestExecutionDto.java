package com.vplmqa.ticket.dto;

import java.util.UUID;

public record TestExecutionDto(
        UUID id,
        UUID projectId,
        String componentCanonicalName,
        String componentHtmlId,
        String status,
        String errorLog
) {}
