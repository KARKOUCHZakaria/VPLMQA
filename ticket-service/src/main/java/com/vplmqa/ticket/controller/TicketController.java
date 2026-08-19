package com.vplmqa.ticket.controller;

import com.vplmqa.common.ApiResponse;
import com.vplmqa.ticket.dto.TicketAttachmentRequest;
import com.vplmqa.ticket.dto.TicketCommentRequest;
import com.vplmqa.ticket.dto.TicketRequest;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.enumtype.SeverityEnum;
import com.vplmqa.ticket.enumtype.TicketStatusEnum;
import com.vplmqa.ticket.service.AttachmentService;
import com.vplmqa.ticket.service.CommentService;
import com.vplmqa.ticket.service.TicketService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final CommentService commentService;
    private final AttachmentService attachmentService;

    public TicketController(TicketService ticketService, CommentService commentService, AttachmentService attachmentService) {
        this.ticketService = ticketService;
        this.commentService = commentService;
        this.attachmentService = attachmentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Ticket>> create(@RequestBody TicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Ticket>>> list(@RequestParam(required = false) UUID projectId,
                                                          @RequestParam(required = false) TicketStatusEnum status,
                                                          @RequestParam(required = false) SeverityEnum severity) {
        if (projectId != null) {
            return ResponseEntity.ok(ApiResponse.ok(ticketService.listByProject(projectId, status, severity)));
        }
        return ResponseEntity.ok(ApiResponse.ok(ticketService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Ticket>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.get(id)));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<List<Ticket>>> listByProject(@PathVariable UUID projectId,
                                                                   @RequestParam(required = false) TicketStatusEnum status,
                                                                   @RequestParam(required = false) SeverityEnum severity) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.listByProject(projectId, status, severity)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<Object>> addComment(@PathVariable UUID id, @RequestBody TicketCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.addComment(id, request)));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<Object>> listComments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.listComments(id)));
    }

    @PostMapping("/{id}/attachments")
    public ResponseEntity<ApiResponse<Object>> addAttachment(@PathVariable UUID id, @RequestBody TicketAttachmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(attachmentService.addAttachment(id, request)));
    }

    @GetMapping("/{id}/attachments")
    public ResponseEntity<ApiResponse<Object>> listAttachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(attachmentService.listAttachments(id)));
    }
}
