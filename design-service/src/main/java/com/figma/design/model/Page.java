package com.figma.design.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "figma_page_id", nullable = true)
    private String figmaPageId;

    @Column(name = "file_link", nullable = true, length = 500)
    private String fileLink;

    @Column(name = "component_count", nullable = true)
    private Integer componentCount;

    @Column(name = "imported_at", nullable = true)
    private LocalDateTime importedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @JsonIgnore
    @OneToMany(mappedBy = "page")
    private List<WebComponent> webComponents = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "page")
    private List<FigmaComponent> figmaComponents = new ArrayList<>();
}
