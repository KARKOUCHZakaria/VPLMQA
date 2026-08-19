package com.vplmqa.ticket.dto;

public record AzureDevOpsMember(
        String id,
        String displayName,
        String email,
        String uniqueName
) {
}
