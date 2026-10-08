package com.vplmqa.ticket.entity;

import com.vplmqa.ticket.enumtype.SeverityEnum;
import com.vplmqa.ticket.enumtype.TicketStatusEnum;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SeverityEnum severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TicketStatusEnum status;
    @Column(name = "comparison_result_id") private UUID comparisonResultId;
    @Column(name = "test_execution_id") private UUID testExecutionId;
    @Column(name = "component_canonical_name") private String componentCanonicalName;
    @Column(name = "component_html_id") private String componentHtmlId;
    @Column(name = "assigned_to") private String assignedTo;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_tags", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "tag", nullable = false, length = 80)
    private Set<String> tags = new LinkedHashSet<>();
    @Column(name = "azure_work_item_id") private Integer azureWorkItemId;
    @Column(name = "azure_work_item_url") private String azureWorkItemUrl;
    @Column(name = "azure_sync_status", nullable = false) private String azureSyncStatus = "NOT_CONFIGURED";
    @Column(name = "azure_sync_error") private String azureSyncError;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getProjectId(){return projectId;} public void setProjectId(UUID projectId){this.projectId=projectId;}
    public String getTitle(){return title;} public void setTitle(String title){this.title=title;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public SeverityEnum getSeverity(){return severity;} public void setSeverity(SeverityEnum severity){this.severity=severity;}
    public TicketStatusEnum getStatus(){return status;} public void setStatus(TicketStatusEnum status){this.status=status;}
    public UUID getComparisonResultId(){return comparisonResultId;} public void setComparisonResultId(UUID comparisonResultId){this.comparisonResultId=comparisonResultId;}
    public UUID getTestExecutionId(){return testExecutionId;} public void setTestExecutionId(UUID testExecutionId){this.testExecutionId=testExecutionId;}
    public String getComponentCanonicalName(){return componentCanonicalName;} public void setComponentCanonicalName(String componentCanonicalName){this.componentCanonicalName=componentCanonicalName;}
    public String getComponentHtmlId(){return componentHtmlId;} public void setComponentHtmlId(String componentHtmlId){this.componentHtmlId=componentHtmlId;}
    public String getAssignedTo(){return assignedTo;} public void setAssignedTo(String assignedTo){this.assignedTo=assignedTo;}
    public Set<String> getTags(){return tags;} public void setTags(Set<String> tags){this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);}
    public Integer getAzureWorkItemId(){return azureWorkItemId;} public void setAzureWorkItemId(Integer azureWorkItemId){this.azureWorkItemId=azureWorkItemId;}
    public String getAzureWorkItemUrl(){return azureWorkItemUrl;} public void setAzureWorkItemUrl(String azureWorkItemUrl){this.azureWorkItemUrl=azureWorkItemUrl;}
    public String getAzureSyncStatus(){return azureSyncStatus;} public void setAzureSyncStatus(String azureSyncStatus){this.azureSyncStatus=azureSyncStatus;}
    public String getAzureSyncError(){return azureSyncError;} public void setAzureSyncError(String azureSyncError){this.azureSyncError=azureSyncError;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant updatedAt){this.updatedAt=updatedAt;}
}
