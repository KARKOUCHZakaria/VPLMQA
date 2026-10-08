package com.vplmqa.ticket.service;

import com.vplmqa.common.EntityNotFoundException;
import com.vplmqa.ticket.dto.TicketRequest;
import com.vplmqa.ticket.entity.Ticket;
import com.vplmqa.ticket.enumtype.SeverityEnum;
import com.vplmqa.ticket.enumtype.TicketStatusEnum;
import com.vplmqa.ticket.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketService {
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AzureDevOpsService azureDevOpsService;

    public TicketService(TicketRepository ticketRepository,
                         KafkaTemplate<String, Object> kafkaTemplate,
                         AzureDevOpsService azureDevOpsService) {
        this.ticketRepository = ticketRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.azureDevOpsService = azureDevOpsService;
    }

    @Transactional
    public Ticket create(TicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setProjectId(request.projectId());
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setSeverity(request.severity() == null ? SeverityEnum.MEDIUM : request.severity());
        ticket.setStatus(request.status() == null ? TicketStatusEnum.OPEN : request.status());
        ticket.setComparisonResultId(request.comparisonResultId());
        ticket.setTestExecutionId(request.testExecutionId());
        ticket.setComponentCanonicalName(request.componentCanonicalName());
        ticket.setComponentHtmlId(request.componentHtmlId());
        ticket.setAssignedTo(request.assignedTo());
        ticket.setTags(request.tags() == null ? new LinkedHashSet<>() : request.tags().stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().replaceAll("\\s+", " "))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Ticket saved = ticketRepository.save(ticket);
        kafkaTemplate.send("ticket.created", saved.getId().toString());
        try {
            AzureDevOpsService.AzureWorkItem workItem = azureDevOpsService.createWorkItem(saved);
            if (workItem != null) {
                saved.setAzureWorkItemId(workItem.id());
                saved.setAzureWorkItemUrl(workItem.url());
                saved.setAzureSyncStatus("SYNCED");
                saved.setAzureSyncError(null);
            }
        } catch (Exception exception) {
            saved.setAzureSyncStatus("FAILED");
            saved.setAzureSyncError(exception.getMessage());
            log.warn("Azure DevOps synchronization failed for local ticket {}: {}",
                    saved.getId(), exception.getMessage());
        }
        return ticketRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public Ticket get(UUID id) {
        return ticketRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Ticket not found"));
    }

    @Transactional(readOnly = true)
    public List<Ticket> list() {
        return ticketRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByProject(UUID projectId) {
        return ticketRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByProject(UUID projectId, TicketStatusEnum status, SeverityEnum severity) {
        if (status != null) {
            return ticketRepository.findByProjectIdAndStatus(projectId, status);
        }
        if (severity != null) {
            return ticketRepository.findByProjectIdAndSeverity(projectId, severity);
        }
        return ticketRepository.findByProjectId(projectId);
    }
}
