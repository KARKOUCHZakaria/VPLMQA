package com.vplmqa.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Page belonging to a project.
 */
@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column
    private String url;

    @Column(nullable = false)
    private String path;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Column(name = "scan_status")
    private String scanStatus;

    @Column(name = "figma_object_path", columnDefinition = "TEXT")
    private String figmaObjectPath;

    @Column(name = "web_object_path", columnDefinition = "TEXT")
    private String webObjectPath;

    @Column(name = "ml_dataset_json_path", columnDefinition = "TEXT")
    private String mlDatasetJsonPath;

    @Column(name = "ml_dataset_csv_path", columnDefinition = "TEXT")
    private String mlDatasetCsvPath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Instant getLastScannedAt() { return lastScannedAt; }
    public void setLastScannedAt(Instant lastScannedAt) { this.lastScannedAt = lastScannedAt; }
    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
    public String getFigmaObjectPath() { return figmaObjectPath; }
    public void setFigmaObjectPath(String figmaObjectPath) { this.figmaObjectPath = figmaObjectPath; }
    public String getWebObjectPath() { return webObjectPath; }
    public void setWebObjectPath(String webObjectPath) { this.webObjectPath = webObjectPath; }
    public String getMlDatasetJsonPath() { return mlDatasetJsonPath; }
    public void setMlDatasetJsonPath(String mlDatasetJsonPath) { this.mlDatasetJsonPath = mlDatasetJsonPath; }
    public String getMlDatasetCsvPath() { return mlDatasetCsvPath; }
    public void setMlDatasetCsvPath(String mlDatasetCsvPath) { this.mlDatasetCsvPath = mlDatasetCsvPath; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
