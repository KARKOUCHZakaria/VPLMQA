package com.vplmqa.ticket.dto;

import java.util.UUID;

public record ComparisonResultDto(
        UUID id,
        UUID projectId,
        String componentCanonicalName,
        String componentHtmlId,
        float confidence,
        String status
) {}
