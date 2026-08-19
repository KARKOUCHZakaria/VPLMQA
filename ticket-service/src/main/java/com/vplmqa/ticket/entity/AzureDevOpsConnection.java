package com.vplmqa.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "azure_devops_connections")
public class AzureDevOpsConnection {
    @Id
    @Column(name = "project_id")
    private UUID projectId;
    @Column(nullable = false)
    private String organization;
    @Column(name = "azure_project", nullable = false)
    private String azureProject;
    @Column(name = "work_item_type", nullable = false)
    private String workItemType = "Bug";
    @Column(name = "area_path")
    private String areaPath;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getAzureProject() { return azureProject; }
    public void setAzureProject(String azureProject) { this.azureProject = azureProject; }
    public String getWorkItemType() { return workItemType; }
    public void setWorkItemType(String workItemType) { this.workItemType = workItemType; }
    public String getAreaPath() { return areaPath; }
    public void setAreaPath(String areaPath) { this.areaPath = areaPath; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
