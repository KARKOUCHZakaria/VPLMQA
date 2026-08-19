package com.figma.design.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String url;

    @Column(nullable = false, unique = true)
    private Integer figmaKey;

    @Column(name = "link_minio", nullable = true, length = 500)
    private String linkMinIO;

    @Column(name = "figma_project_name", nullable = true)
    private String figmaProjectName;

    @Column(name = "figma_design_file_id", nullable = true)
    private String figmaDesignFileId;

    @Column(name = "design_implementation_status", nullable = true)
    private String designImplementationStatus;

    @Column(name = "last_design_sync_at", nullable = true)
    private LocalDateTime lastDesignSyncAt;

    @Column(name = "is_design_synced", nullable = false)
    private Boolean isDesignSynced = false;

    @JsonIgnore
    @OneToMany(mappedBy = "project")
    private List<Page> pages = new ArrayList<>();
}
