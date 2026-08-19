package com.vplmqa.project.entity;

import com.vplmqa.project.enumtype.ComponentSourceEnum;
import com.vplmqa.project.enumtype.ComponentStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Component definition tied to a page.
 */
@Entity
@Table(name = "components")
public class Component {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    @Column(name = "semantic_role")
    private String semanticRole;

    @Column(name = "functional_meaning", columnDefinition = "TEXT")
    private String functionalMeaning;

    @Column(name = "html_id")
    private String htmlId;

    @Column(name = "figma_node_id")
    private String figmaNodeId;

    @Column(name = "test_identifier")
    private String testIdentifier;

    @Column(name = "css_selector", columnDefinition = "TEXT")
    private String cssSelector;

    @Column(name = "xpath", columnDefinition = "TEXT")
    private String xpath;

    @Column(name = "object_path", columnDefinition = "TEXT")
    private String objectPath;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentSourceEnum source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentStatusEnum status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "css_properties", columnDefinition = "jsonb")
    private Map<String, Object> cssProperties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bounding_box", columnDefinition = "jsonb")
    private Map<String, Object> boundingBox;

    @Column(name = "screenshot")
    private String screenshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Page getPage() { return page; }
    public void setPage(Page page) { this.page = page; }
    public String getCanonicalName() { return canonicalName; }
    public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }
    public String getSemanticRole() { return semanticRole; }
    public void setSemanticRole(String semanticRole) { this.semanticRole = semanticRole; }
    public String getFunctionalMeaning() { return functionalMeaning; }
    public void setFunctionalMeaning(String functionalMeaning) { this.functionalMeaning = functionalMeaning; }
    public String getHtmlId() { return htmlId; }
    public void setHtmlId(String htmlId) { this.htmlId = htmlId; }
    public String getFigmaNodeId() { return figmaNodeId; }
    public void setFigmaNodeId(String figmaNodeId) { this.figmaNodeId = figmaNodeId; }
    public String getTestIdentifier() { return testIdentifier; }
    public void setTestIdentifier(String testIdentifier) { this.testIdentifier = testIdentifier; }
    public String getCssSelector() { return cssSelector; }
    public void setCssSelector(String cssSelector) { this.cssSelector = cssSelector; }
    public String getXpath() { return xpath; }
    public void setXpath(String xpath) { this.xpath = xpath; }
    public String getObjectPath() { return objectPath; }
    public void setObjectPath(String objectPath) { this.objectPath = objectPath; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public ComponentSourceEnum getSource() { return source; }
    public void setSource(ComponentSourceEnum source) { this.source = source; }
    public ComponentStatusEnum getStatus() { return status; }
    public void setStatus(ComponentStatusEnum status) { this.status = status; }
    public Map<String, Object> getCssProperties() { return cssProperties; }
    public void setCssProperties(Map<String, Object> cssProperties) { this.cssProperties = cssProperties; }
    public Map<String, Object> getBoundingBox() { return boundingBox; }
    public void setBoundingBox(Map<String, Object> boundingBox) { this.boundingBox = boundingBox; }
    public String getScreenshot() { return screenshot; }
    public void setScreenshot(String screenshot) { this.screenshot = screenshot; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
