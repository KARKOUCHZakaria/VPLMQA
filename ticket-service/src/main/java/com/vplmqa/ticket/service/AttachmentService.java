package com.vplmqa.ticket.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.ticket.dto.TicketAttachmentRequest;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.entity.TicketAttachment;
import com.vplmqa.ticket.repository.TicketAttachmentRepository;
import com.vplmqa.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private final TicketRepository ticketRepository;
    private final TicketAttachmentRepository attachmentRepository;

    public AttachmentService(TicketRepository ticketRepository, TicketAttachmentRepository attachmentRepository) {
        this.ticketRepository = ticketRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional
    public TicketAttachment addAttachment(UUID ticketId, TicketAttachmentRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicket(ticket);
        attachment.setFileName(request.fileName());
        attachment.setStorageUrl(request.storageUrl());
        attachment.setContentType(request.contentType());
        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public List<TicketAttachment> listAttachments(UUID ticketId) {
        return attachmentRepository.findByTicketId(ticketId);
    }
}
