package com.vplmqa.ticket.dto;

import java.util.UUID;

public record TicketCommentRequest(UUID authorId, String body) {
}
