package com.vplmqa.ticket.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.ticket.dto.TicketAttachmentRequest;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.entity.TicketAttachment;
import com.vplmqa.ticket.repository.TicketAttachmentRepository;
import com.vplmqa.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Base64;
import java.util.UUID;

/**
 * Persists ticket evidence locally and forwards it to Azure DevOps when the
 * ticket has already been synchronized. Remote URLs are not fetched; only
 * artifacts served by the local E2E agent are accepted.
 */
@Service
public class AttachmentService {

    private final TicketRepository ticketRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final AzureDevOpsService azureDevOpsService;

    public AttachmentService(TicketRepository ticketRepository, TicketAttachmentRepository attachmentRepository,
                             AzureDevOpsService azureDevOpsService) {
        this.ticketRepository = ticketRepository;
        this.attachmentRepository = attachmentRepository;
        this.azureDevOpsService = azureDevOpsService;
    }

    @Transactional
    public TicketAttachment addAttachment(UUID ticketId, TicketAttachmentRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicket(ticket);
        attachment.setFileName(request.fileName());
        attachment.setStorageUrl(request.storageUrl());
        attachment.setContentType(request.contentType());
        TicketAttachment saved = attachmentRepository.save(attachment);
        if (ticket.getAzureWorkItemId() != null) {
            azureDevOpsService.attachToWorkItem(ticket, request.fileName(), request.contentType(), readContent(request));
        }
        return saved;
    }

    private byte[] readContent(TicketAttachmentRequest request) {
        if (request.base64Content() != null && !request.base64Content().isBlank()) {
            return Base64.getDecoder().decode(request.base64Content());
        }
        if (request.storageUrl() == null || request.storageUrl().isBlank()) {
            throw new IllegalArgumentException("Attachment content or storage URL is required");
        }
        try {
            URI artifactUri = URI.create(request.storageUrl());
// This allowlist prevents the ticket service from becoming a
            // server-side request forgery proxy for arbitrary URLs.
            boolean isLocalE2EArtifact = "http".equalsIgnoreCase(artifactUri.getScheme())
                    && "localhost".equalsIgnoreCase(artifactUri.getHost())
                    && artifactUri.getPort() == 8090
                    && artifactUri.getPath() != null
                    && artifactUri.getPath().startsWith("/artifacts/");
            if (!isLocalE2EArtifact) {
                throw new IllegalArgumentException("Only local E2E artifact URLs can be retrieved by Ticket service");
            }
            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(artifactUri).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Unable to retrieve local E2E evidence (HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to retrieve local E2E evidence", exception);
        }
    }
    @Transactional(readOnly = true)
    public List<TicketAttachment> listAttachments(UUID ticketId) {
        return attachmentRepository.findByTicketId(ticketId);
    }
}
