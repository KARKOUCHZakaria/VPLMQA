package com.vplmqa.analytics.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String,Object> payload;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getProjectId(){return projectId;} public void setProjectId(UUID projectId){this.projectId=projectId;}
    public String getEventType(){return eventType;} public void setEventType(String eventType){this.eventType=eventType;}
    public Map<String,Object> getPayload(){return payload;} public void setPayload(Map<String,Object> payload){this.payload=payload;}
    public Instant getOccurredAt(){return occurredAt;} public void setOccurredAt(Instant occurredAt){this.occurredAt=occurredAt;}
}
