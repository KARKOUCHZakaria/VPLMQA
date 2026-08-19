package com.vplmqa.analytics.dto;

public record MetricsResponse(double mismatchRate, double testPassRate, double tokenCoverage, double healthScore) {
}
