package com.vplmqa.analytics.entity;

import com.vplmqa.analytics.enumtype.MetricTypeEnum;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_metrics")
public class ProjectMetric {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(name = "metric_type", nullable = false) private MetricTypeEnum metricType;
    @Column(nullable = false) private double value;
    @Column(nullable = false) private String period;
    @CreationTimestamp @Column(name = "computed_at", nullable = false, updatable = false) private Instant computedAt;
    public UUID getId(){return id;} public void setId(UUID id){this.id=id;}
    public UUID getProjectId(){return projectId;} public void setProjectId(UUID projectId){this.projectId=projectId;}
    public MetricTypeEnum getMetricType(){return metricType;} public void setMetricType(MetricTypeEnum metricType){this.metricType=metricType;}
    public double getValue(){return value;} public void setValue(double value){this.value=value;}
    public String getPeriod(){return period;} public void setPeriod(String period){this.period=period;}
    public Instant getComputedAt(){return computedAt;} public void setComputedAt(Instant computedAt){this.computedAt=computedAt;}
}
