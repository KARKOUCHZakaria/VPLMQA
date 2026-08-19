package com.figma.design.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "figma_components")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FigmaComponent extends Component {

    @Column(name = "figma_node_id")
    private String figmaNodeId;

    @Column(name = "figma_node_name")
    private String figmaNodeName;

    @Column(name = "figma_node_type")
    private String figmaNodeType;

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

    @Column(name = "node_width")
    private Double nodeWidth;

    @Column(name = "node_height")
    private Double nodeHeight;

    @Column(name = "source_file_key")
    private String sourceFileKey;

    @Column(name = "source_file_name")
    private String sourceFileName;

    @Column(name = "file_link", nullable = true, length = 500)
    private String fileLink;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    public FigmaComponent(Long id, ComponentType type, Page page) {
        super(id, type, page);
    }

    @Override
    public void operation() {
        // Intentionally empty: concrete behavior can be added by the transformation pipeline.
    }
}
