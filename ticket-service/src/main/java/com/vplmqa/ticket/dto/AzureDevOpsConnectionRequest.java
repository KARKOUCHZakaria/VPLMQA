package com.vplmqa.ticket.dto;

public record AzureDevOpsConnectionRequest(
        String organization,
        String azureProject,
        String workItemType,
        String areaPath,
        String personalAccessToken,
        Boolean enabled
) {
}
