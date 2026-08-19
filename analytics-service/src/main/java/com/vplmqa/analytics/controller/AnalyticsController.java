package com.vplmqa.analytics.controller;

import com.vplmqa.analytics.dto.MetricsResponse;
import com.vplmqa.analytics.dto.SimilarComponentRequest;
import com.vplmqa.analytics.entity.ComponentEmbedding;
import com.vplmqa.analytics.service.EmbeddingService;
import com.vplmqa.analytics.service.MetricService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final MetricService metricService;
    private final EmbeddingService embeddingService;

    public AnalyticsController(MetricService metricService, EmbeddingService embeddingService) {
        this.metricService = metricService;
        this.embeddingService = embeddingService;
    }

    @GetMapping("/projects/{id}/metrics")
    public ResponseEntity<MetricsResponse> metrics(@org.springframework.web.bind.annotation.PathVariable UUID id) {
        return ResponseEntity.ok(metricService.getProjectHealthScore(id));
    }

    @GetMapping("/projects/{id}/health-score")
    public ResponseEntity<Double> health(@org.springframework.web.bind.annotation.PathVariable UUID id) {
        return ResponseEntity.ok(metricService.getProjectHealthScore(id).healthScore());
    }

    @PostMapping("/components/similar")
    public ResponseEntity<List<ComponentEmbedding>> similar(@RequestBody SimilarComponentRequest request) {
        return ResponseEntity.ok(embeddingService.findSimilar(request.embedding(), request.projectId(), request.topK()));
    }
}
