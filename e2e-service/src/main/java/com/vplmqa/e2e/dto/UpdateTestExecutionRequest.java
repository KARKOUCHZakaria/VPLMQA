package com.vplmqa.e2e.dto;

public record UpdateTestExecutionRequest(
    String status,
    String outputLog,
    String errorMessage,
    Long executionTimeMs,
    String screenshots
) {}
