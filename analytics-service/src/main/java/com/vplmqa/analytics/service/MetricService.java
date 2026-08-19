package com.vplmqa.analytics.service;

import com.vplmqa.analytics.dto.MetricsResponse;
import com.vplmqa.analytics.repository.ProjectMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class MetricService {

    private final ProjectMetricRepository projectMetricRepository;

    public MetricService(ProjectMetricRepository projectMetricRepository) {
        this.projectMetricRepository = projectMetricRepository;
    }

    @Transactional(readOnly = true)
    public MetricsResponse getProjectHealthScore(UUID projectId) {
        return new MetricsResponse(getMismatchRate(projectId, new DateRange(null, null)), getTestPassRate(projectId, new DateRange(null, null)), getTokenCoverage(projectId, new DateRange(null, null)), 0.0);
    }

    @Transactional(readOnly = true)
    public double getMismatchRate(UUID projectId, DateRange range) { return 0.0; }
    @Transactional(readOnly = true)
    public double getTestPassRate(UUID projectId, DateRange range) { return 0.0; }
    @Transactional(readOnly = true)
    public double getTokenCoverage(UUID projectId, DateRange range) { return 0.0; }

    public record DateRange(Date start, Date end) { }
}
