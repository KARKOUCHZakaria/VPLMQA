package com.vplmqa.e2e.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record GherkinE2ERunRequest(
    @NotBlank(message = "Gherkin text is required")
    String gherkin,
    Boolean headless,
    Boolean repairOnFailure,
    Integer maxRepairAttempts,
    Boolean execute,
    UUID projectId,
    UUID pageId
) {}
