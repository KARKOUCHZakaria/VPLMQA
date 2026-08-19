package com.vplmqa.e2e.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "features")
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "gherkin_content", columnDefinition = "TEXT")
    private String gherkinContent;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private FeatureStatus status = FeatureStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "default_page_id")
    private UUID defaultPageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", nullable = false, length = 20)
    private TargetMode targetMode = TargetMode.EXTERNAL;

    @OneToMany(mappedBy = "feature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Scenario> scenarios = new ArrayList<>();

    @OneToMany(mappedBy = "feature", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TestExecution> testExecutions = new ArrayList<>();

    // Constructors, Getters, and Setters
    public Feature() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGherkinContent() { return gherkinContent; }
    public void setGherkinContent(String gherkinContent) { this.gherkinContent = gherkinContent; }
    public FeatureStatus getStatus() { return status; }
    public void setStatus(FeatureStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getDefaultPageId() { return defaultPageId; }
    public void setDefaultPageId(UUID defaultPageId) { this.defaultPageId = defaultPageId; }
    public TargetMode getTargetMode() { return targetMode; }
    public void setTargetMode(TargetMode targetMode) { this.targetMode = targetMode; }
    public List<Scenario> getScenarios() { return scenarios; }
    public void setScenarios(List<Scenario> scenarios) { this.scenarios = scenarios; }
    public List<TestExecution> getTestExecutions() { return testExecutions; }
    public void setTestExecutions(List<TestExecution> testExecutions) { this.testExecutions = testExecutions; }
}
