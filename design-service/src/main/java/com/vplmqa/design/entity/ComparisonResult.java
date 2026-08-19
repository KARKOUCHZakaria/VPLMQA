package com.vplmqa.design.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "comparison_results")
public class ComparisonResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "figma_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> figmaSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "web_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> webSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> diff;

    private float confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComparisonStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum ComparisonStatus { MATCH, MISMATCH, IGNORED }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getComponentId() { return componentId; }
    public void setComponentId(UUID componentId) { this.componentId = componentId; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public Map<String, Object> getFigmaSnapshot() { return figmaSnapshot; }
    public void setFigmaSnapshot(Map<String, Object> figmaSnapshot) { this.figmaSnapshot = figmaSnapshot; }
    public Map<String, Object> getWebSnapshot() { return webSnapshot; }
    public void setWebSnapshot(Map<String, Object> webSnapshot) { this.webSnapshot = webSnapshot; }
    public Map<String, Object> getDiff() { return diff; }
    public void setDiff(Map<String, Object> diff) { this.diff = diff; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = confidence; }
    public ComparisonStatus getStatus() { return status; }
    public void setStatus(ComparisonStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
