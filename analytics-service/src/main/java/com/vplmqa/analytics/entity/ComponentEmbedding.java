package com.vplmqa.analytics.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "component_embeddings")
public class ComponentEmbedding {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "component_id", nullable = false) private UUID componentId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VECTOR)
    @Column(columnDefinition = "vector(1536)") private float[] embedding;
    @Column(name = "canonical_name") private String canonicalName;
    @Column(name = "semantic_role") private String semanticRole;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getComponentId(){return componentId;} public void setComponentId(UUID componentId){this.componentId=componentId;}
    public UUID getProjectId(){return projectId;} public void setProjectId(UUID projectId){this.projectId=projectId;}
    public float[] getEmbedding(){return embedding;} public void setEmbedding(float[] embedding){this.embedding=embedding;}
    public String getCanonicalName(){return canonicalName;} public void setCanonicalName(String canonicalName){this.canonicalName=canonicalName;}
    public String getSemanticRole(){return semanticRole;} public void setSemanticRole(String semanticRole){this.semanticRole=semanticRole;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
}
