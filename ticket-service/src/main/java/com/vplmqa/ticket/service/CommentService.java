package com.vplmqa.ticket.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.ticket.dto.TicketCommentRequest;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.entity.TicketComment;
import com.vplmqa.ticket.repository.TicketCommentRepository;
import com.vplmqa.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CommentService {

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;

    public CommentService(TicketRepository ticketRepository, TicketCommentRepository ticketCommentRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
    }

    @Transactional
    public TicketComment addComment(UUID ticketId, TicketCommentRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthorId(request.authorId());
        comment.setBody(request.body());
        return ticketCommentRepository.save(comment);
    }

    @Transactional(readOnly = true)
    public List<TicketComment> listComments(UUID ticketId) {
        return ticketCommentRepository.findByTicketId(ticketId);
    }
}
