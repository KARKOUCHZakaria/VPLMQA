package com.vplmqa.ticket.dto;

public record TicketAttachmentRequest(String fileName, String storageUrl, String contentType) {
}
