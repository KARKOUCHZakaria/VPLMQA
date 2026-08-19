package com.vplmqa.ticket.dto;

import java.util.UUID;

public record AzureDevOpsConnectionResponse(
        UUID projectId,
        String organization,
        String azureProject,
        String workItemType,
        String areaPath,
        boolean enabled,
        boolean credentialStored
) {
}
