package com.vplmqa.e2e.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vplmqa.e2e.dto.*;
import com.vplmqa.e2e.entities.*;
import com.vplmqa.e2e.repositories.FeatureRepository;
import com.vplmqa.e2e.repositories.TestExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final GherkinE2EAutomationService automationService;
    private final TestExecutionRepository testExecutionRepository;
    private final ObjectMapper objectMapper;
    private final Set<UUID> activeFeatureRuns = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> recentlyFinishedFeatureRuns = new ConcurrentHashMap<>();
    private static final long FEATURE_RUN_REPLAY_WINDOW_MS = 30_000L;

    public FeatureService(FeatureRepository featureRepository,
                          GherkinE2EAutomationService automationService,
                          TestExecutionRepository testExecutionRepository,
                          ObjectMapper objectMapper) {
        this.featureRepository = featureRepository;
        this.automationService = automationService;
        this.testExecutionRepository = testExecutionRepository;
        this.objectMapper = objectMapper;
    }

    public synchronized FeatureResponse createFeature(CreateFeatureRequest request) {
        Feature duplicate = findRecentDuplicateFeature(request);
        if (duplicate != null) {
            return mapToResponse(duplicate);
        }

        Feature feature = new Feature();
        feature.setName(request.name());
        feature.setDescription(request.description());
        feature.setGherkinContent(request.gherkinContent());
        feature.setProjectId(request.projectId());
        feature.setDefaultPageId(null);
        feature.setTargetMode(request.targetMode() == null
            ? (request.projectId() == null ? TargetMode.EXTERNAL : TargetMode.PROJECT)
            : request.targetMode());
        feature.setStatus(FeatureStatus.DRAFT);
        
        feature = featureRepository.save(feature);
        return mapToResponse(feature);
    }

    private Feature findRecentDuplicateFeature(CreateFeatureRequest request) {
        String requestedName = request.name() == null ? "" : request.name().trim();
        String requestedGherkin = normalizeGherkin(request.gherkinContent());
        UUID requestedProjectId = request.projectId();
        TargetMode requestedTargetMode = request.targetMode() == null
            ? (requestedProjectId == null ? TargetMode.EXTERNAL : TargetMode.PROJECT)
            : request.targetMode();
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(5);

        return featureRepository.findAll().stream()
            .filter(feature -> feature.getCreatedAt() != null && feature.getCreatedAt().isAfter(cutoff))
            .filter(feature -> requestedName.equals((feature.getName() == null ? "" : feature.getName().trim())))
            .filter(feature -> java.util.Objects.equals(requestedProjectId, feature.getProjectId()))
            .filter(feature -> requestedTargetMode == feature.getTargetMode())
            .filter(feature -> requestedGherkin.equals(normalizeGherkin(feature.getGherkinContent())))
            .sorted(java.util.Comparator.comparing(Feature::getCreatedAt).reversed())
            .findFirst()
            .orElse(null);
    }

    private String normalizeGherkin(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
    public FeatureResponse getFeature(UUID id) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
        return mapToResponse(feature);
    }

    public FeatureWithHierarchyResponse getFeatureWithHierarchy(UUID id) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
        
        List<ScenarioWithStepsResponse> scenarioResponses = feature.getScenarios().stream()
            .sorted(java.util.Comparator.comparingInt(scenario -> scenario.getSequenceOrder() == null ? Integer.MAX_VALUE : scenario.getSequenceOrder()))
            .map(this::mapScenarioWithSteps)
            .collect(Collectors.toList());

        return new FeatureWithHierarchyResponse(
            feature.getId(),
            feature.getName(),
            feature.getDescription(),
            feature.getGherkinContent(),
            feature.getStatus(),
            feature.getCreatedAt(),
            feature.getUpdatedAt(),
            feature.getCreatedBy(),
            feature.getProjectId(),
            feature.getDefaultPageId(),
            feature.getTargetMode(),
            scenarioResponses
        );
    }

    public FeatureResponse updateFeature(UUID id, UpdateFeatureRequest request) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));

        if (request.name() != null) feature.setName(request.name());
        if (request.description() != null) feature.setDescription(request.description());
        if (request.gherkinContent() != null) feature.setGherkinContent(request.gherkinContent());
        if (request.projectId() != null) feature.setProjectId(request.projectId());
        if (request.defaultPageId() != null) feature.setDefaultPageId(request.defaultPageId());
        if (request.targetMode() != null) feature.setTargetMode(request.targetMode());
        if (request.status() != null) {
            try {
                feature.setStatus(FeatureStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Ignore or handle invalid status
            }
        }

        feature = featureRepository.save(feature);
        return mapToResponse(feature);
    }

    public void deleteFeature(UUID id) {
        featureRepository.deleteById(id);
    }

    public Page<FeatureResponse> listFeatures(int page, int size) {
        return featureRepository.findAll(PageRequest.of(page, size))
            .map(this::mapToResponse);
    }

    public String generateGherkinFromFeature(UUID id) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
        ensureSequenceOrders(feature);
        
        StringBuilder gherkin = new StringBuilder();
        gherkin.append("Feature: ").append(feature.getName()).append("\n");
        if (feature.getDescription() != null && !feature.getDescription().isEmpty()) {
            gherkin.append("  ").append(feature.getDescription()).append("\n");
        }
        gherkin.append("\n");

        for (Scenario scenario : feature.getScenarios().stream()
                .sorted(java.util.Comparator.comparingInt(this::scenarioOrder))
                .toList()) {
            gherkin.append("  Scenario: ").append(scenario.getName()).append("\n");
            for (Step step : scenario.getSteps().stream()
                    .sorted(java.util.Comparator.comparingInt(this::stepOrder))
                    .toList()) {
                gherkin.append("    ").append(step.getType()).append(" ").append(step.getText()).append("\n");
            }
            gherkin.append("\n");
        }

        String generated = gherkin.toString().trim();
        feature.setGherkinContent(generated);
        featureRepository.save(feature);
        return generated;
    }

    public java.util.Map<String, Object> runAgentPipeline(UUID id) {
        long requestedAt = System.currentTimeMillis();
        Long lastFinishedAt = recentlyFinishedFeatureRuns.get(id);
        if (lastFinishedAt != null && requestedAt - lastFinishedAt < FEATURE_RUN_REPLAY_WINDOW_MS) {
            return java.util.Map.of(
                    "success", false,
                    "status", "recently_finished",
                    "duplicate_run", true,
                    "feature_id", id.toString(),
                    "message", "This feature finished moments ago. Replay run request ignored."
            );
        }
        if (!activeFeatureRuns.add(id)) {
            return java.util.Map.of(
                    "success", false,
                    "status", "already_running",
                    "duplicate_run", true,
                    "feature_id", id.toString(),
                    "message", "This feature already has an active E2E execution. Duplicate run request ignored."
            );
        }

        long startedAt = System.currentTimeMillis();
        try {
            Feature feature = featureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature not found"));
            ensureSequenceOrders(feature);
            markRunStatus(feature, FeatureStatus.RUNNING, ScenarioStatus.RUNNING, StepStatus.PENDING);
            featureRepository.save(feature);

            String gherkin = generateGherkinFromFeature(id);
            java.util.Map<String, Object> result = automationService.run(new GherkinE2ERunRequest(
                gherkin,
                false,
                true,
                1,
                true,
                feature.getProjectId(),
                null
            ));
            boolean success = isSuccessfulRun(result);
            applyRunResultStatus(feature, result, success);
            featureRepository.save(feature);
            persistExecutionEvidence(feature, result, success, System.currentTimeMillis() - startedAt);
            return result;
        } finally {
            activeFeatureRuns.remove(id);
            recentlyFinishedFeatureRuns.put(id, System.currentTimeMillis());
            cleanupRecentFeatureRuns();
        }
    }

    private void cleanupRecentFeatureRuns() {
        long cutoff = System.currentTimeMillis() - FEATURE_RUN_REPLAY_WINDOW_MS;
        recentlyFinishedFeatureRuns.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private void persistExecutionEvidence(Feature feature, java.util.Map<String, Object> result,
                                          boolean success, long durationMs) {
        Scenario failedScenario = failedScenario(feature, result);
        java.util.Map<?, ?> stoppedAt = result.get("stopped_at") instanceof java.util.Map<?, ?> value
                ? value : java.util.Map.of();
        java.util.Map<?, ?> error = result.get("error") instanceof java.util.Map<?, ?> value
                ? value : java.util.Map.of();
        String exception = value(error, "message", String.valueOf(result.getOrDefault("message", "")));
        String stderr = value(error, "stderr", "");
        String screenshot = value(error, "screenshot", value(result, "screenshot", ""));
        java.util.List<String> featureEvidenceScreenshots = evidenceScreenshotUrls(result, null);
        if (!success && failedScenario == null) {
            TestExecution execution = new TestExecution();
            execution.setFeature(feature);
            execution.setScenario(null);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setExecutionTimeMs(durationMs);
            execution.setOutputLog(buildFeatureExecutionLog(feature, result, stoppedAt, exception, stderr));
            execution.setErrorMessage(exception);
            java.util.List<String> screenshots = new java.util.ArrayList<>();
            if (!screenshot.isBlank()) screenshots.add(screenshot);
            screenshots.addAll(featureEvidenceScreenshots);
            execution.setScreenshots(screenshotsJson(screenshots));
            testExecutionRepository.save(execution);
        }

        java.util.List<Scenario> orderedScenarios = feature.getScenarios().stream()
                .sorted(java.util.Comparator.comparingInt(this::scenarioOrder))
                .toList();

        for (Scenario scenario : orderedScenarios) {
            boolean scenarioFailed = !success && failedScenario != null && failedScenario.getId().equals(scenario.getId());
            boolean scenarioPassed = success || (!scenarioFailed && scenario.getStatus() == ScenarioStatus.PASSED);
            TestExecution execution = new TestExecution();
            execution.setFeature(feature);
            execution.setScenario(scenario);
            execution.setStatus(scenarioFailed ? ExecutionStatus.FAILED : scenarioPassed ? ExecutionStatus.PASSED : ExecutionStatus.PENDING);
            execution.setExecutionTimeMs(durationMs);
            execution.setOutputLog(buildScenarioExecutionLog(feature, scenario, scenarioFailed, success, stoppedAt, exception, stderr));
            execution.setErrorMessage(scenarioFailed ? exception : "");
            java.util.List<String> screenshots = new java.util.ArrayList<>();
            if (scenarioFailed && !screenshot.isBlank()) screenshots.add(screenshot);
            screenshots.addAll(evidenceScreenshotUrls(result, scenario.getName()));
            execution.setScreenshots(screenshotsJson(screenshots));
            testExecutionRepository.save(execution);
        }
    }

    private java.util.List<String> evidenceScreenshotUrls(java.util.Map<String, Object> result, String scenarioName) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        Object evidence = result.get("search_result_evidence");
        if (!(evidence instanceof java.util.List<?> evidenceItems)) {
            return urls;
        }
        for (Object evidenceItem : evidenceItems) {
            if (!(evidenceItem instanceof java.util.Map<?, ?> evidenceMap)) {
                continue;
            }
            String evidenceScenario = value(evidenceMap, "scenario", "");
            if (scenarioName != null && !evidenceScenario.isBlank() && !scenarioName.equals(evidenceScenario)) {
                continue;
            }
            Object screenshots = evidenceMap.get("screenshots");
            if (!(screenshots instanceof java.util.List<?> screenshotItems)) {
                continue;
            }
            for (Object screenshotItem : screenshotItems) {
                String url = "";
                if (screenshotItem instanceof java.util.Map<?, ?> screenshotMap) {
                    url = value(screenshotMap, "url", "");
                } else if (screenshotItem != null) {
                    url = String.valueOf(screenshotItem);
                }
                if (!url.isBlank() && !urls.contains(url)) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private String screenshotsJson(java.util.List<String> screenshots) {
        try {
            return objectMapper.writeValueAsString(screenshots == null ? java.util.List.of() : screenshots);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private String buildFeatureExecutionLog(Feature feature, java.util.Map<String, Object> result,
                                            java.util.Map<?, ?> stoppedAt, String exception, String stderr) {
        StringBuilder log = new StringBuilder()
                .append("Status: FAILED").append('\n')
                .append("Feature: ").append(feature.getName()).append('\n')
                .append("Run at: ").append(java.time.LocalDateTime.now()).append('\n')
                .append("Failure scope: E2E agent startup or orchestration").append('\n');
        if (!stoppedAt.isEmpty()) {
            log.append("Stopped at: ").append(stoppedAt).append('\n');
        }
        if (!exception.isBlank()) {
            log.append("Exception: ").append(exception).append('\n');
        }
        Object attempted = result.get("attempted_agent_urls");
        if (attempted != null) {
            log.append("Attempted agent URLs: ").append(attempted).append('\n');
        }
        Object rawError = result.get("error");
        if (rawError != null && !String.valueOf(rawError).isBlank() && !String.valueOf(rawError).equals(exception)) {
            log.append("Error details: ").append(rawError).append('\n');
        }
        if (!stderr.isBlank() && !stderr.equals(exception)) {
            log.append("Details: ").append(stderr).append('\n');
        }
        if (result.get("steps_executed") != null) {
            log.append("Steps executed before failure: ").append(result.get("steps_executed")).append('\n');
        }
        return log.toString().trim();
    }

    private String buildScenarioExecutionLog(Feature feature, Scenario scenario, boolean scenarioFailed, boolean featureSuccess,
                                             java.util.Map<?, ?> stoppedAt, String exception, String stderr) {
        StringBuilder log = new StringBuilder()
                .append("Status: ").append(scenarioFailed ? "FAILED" : featureSuccess || scenario.getStatus() == ScenarioStatus.PASSED ? "PASSED" : "PENDING").append('\n')
                .append("Feature: ").append(feature.getName()).append('\n')
                .append("Scenario: ").append(scenario.getName()).append('\n')
                .append("Run at: ").append(java.time.LocalDateTime.now()).append('\n');
        if (scenarioFailed) {
            if (!stoppedAt.isEmpty()) {
                log.append("Failed step: ").append(value(stoppedAt, "step_index", "?"))
                        .append(" - ").append(value(stoppedAt, "step", "")).append('\n');
            }
            if (!exception.isBlank()) log.append("Exception: ").append(exception).append('\n');
            if (!stderr.isBlank() && !stderr.equals(exception)) log.append("Details: ").append(stderr).append('\n');
        } else if (featureSuccess || scenario.getStatus() == ScenarioStatus.PASSED) {
            log.append("Scenario completed successfully.");
        } else {
            log.append("Scenario was not executed after an earlier failure.");
        }
        return log.toString().trim();
    }

    private Scenario failedScenario(Feature feature, java.util.Map<String, Object> result) {
        if (!(result.get("stopped_at") instanceof java.util.Map<?, ?> stoppedAt)) return null;
        String name = value(stoppedAt, "scenario", "");
        return feature.getScenarios().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElse(null);
    }

    private void ensureSequenceOrders(Feature feature) {
        int scenarioIndex = 1;
        for (Scenario scenario : feature.getScenarios()) {
            if (scenario.getSequenceOrder() == null) {
                scenario.setSequenceOrder(scenarioIndex);
            }
            int stepIndex = 1;
            for (Step step : scenario.getSteps()) {
                if (step.getSequenceOrder() == null) {
                    step.setSequenceOrder(stepIndex);
                }
                stepIndex++;
            }
            scenarioIndex++;
        }
    }

    private int scenarioOrder(Scenario scenario) {
        return scenario.getSequenceOrder() == null ? Integer.MAX_VALUE : scenario.getSequenceOrder();
    }

    private int stepOrder(Step step) {
        return step.getSequenceOrder() == null ? Integer.MAX_VALUE : step.getSequenceOrder();
    }

    private String value(java.util.Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean isSuccessfulRun(java.util.Map<String, Object> result) {
        Object success = result.get("success");
        if (success instanceof Boolean bool) {
            return bool;
        }
        Object status = result.get("status");
        return status != null && "passed".equalsIgnoreCase(status.toString());
    }

    private void markRunStatus(Feature feature, FeatureStatus featureStatus, ScenarioStatus scenarioStatus, StepStatus stepStatus) {
        feature.setStatus(featureStatus);
        for (Scenario scenario : feature.getScenarios().stream()
                .sorted(java.util.Comparator.comparingInt(this::scenarioOrder))
                .toList()) {
            scenario.setStatus(scenarioStatus);
            for (Step step : scenario.getSteps()) {
                step.setStatus(stepStatus);
            }
        }
    }

    private void applyRunResultStatus(Feature feature, java.util.Map<String, Object> result, boolean success) {
        if (success) {
            markRunStatus(feature, FeatureStatus.PASSED, ScenarioStatus.PASSED, StepStatus.PASSED);
            return;
        }

        feature.setStatus(FeatureStatus.FAILED);

        String failedScenarioName = null;
        Integer failedStepIndex = null;
        Object stoppedAt = result.get("stopped_at");
        if (stoppedAt instanceof java.util.Map<?, ?> stoppedAtMap) {
            Object scenario = stoppedAtMap.get("scenario");
            Object stepIndex = stoppedAtMap.get("step_index");
            failedScenarioName = scenario == null ? null : scenario.toString();
            failedStepIndex = toInteger(stepIndex);
        }

        int stepsExecuted = toInteger(result.get("steps_executed"), 0);
        int globalCompleted = Math.max(0, stepsExecuted - 1);
        if (globalCompleted == 0 && failedStepIndex != null && failedStepIndex > 1) {
            globalCompleted = failedStepIndex - 1;
        }
        if (failedScenarioName == null && failedStepIndex == null && stepsExecuted == 0) {
            for (Scenario scenario : feature.getScenarios().stream()
                    .sorted(java.util.Comparator.comparingInt(value -> value.getSequenceOrder() == null ? Integer.MAX_VALUE : value.getSequenceOrder()))
                    .toList()) {
                scenario.setStatus(ScenarioStatus.DRAFT);
                for (Step step : scenario.getSteps().stream()
                        .sorted(java.util.Comparator.comparingInt(this::stepOrder))
                        .toList()) {
                    step.setStatus(StepStatus.PENDING);
                }
            }
            return;
        }

        int failedGlobalStep = failedScenarioName == null ? Math.max(1, stepsExecuted) : -1;
        int seen = 0;
        boolean reachedFailedScenario = false;
        for (Scenario scenario : feature.getScenarios().stream()
                .sorted(java.util.Comparator.comparingInt(value -> value.getSequenceOrder() == null ? Integer.MAX_VALUE : value.getSequenceOrder()))
                .toList()) {
            java.util.List<Step> orderedSteps = scenario.getSteps().stream()
                    .sorted(java.util.Comparator.comparingInt(this::stepOrder))
                    .toList();
            int firstScenarioStep = seen + 1;
            int lastScenarioStep = seen + orderedSteps.size();
            boolean isFailedScenario = failedScenarioName == null
                    ? failedGlobalStep >= firstScenarioStep && failedGlobalStep <= Math.max(lastScenarioStep, firstScenarioStep)
                    : scenario.getName().equals(failedScenarioName);
            if (isFailedScenario) {
                reachedFailedScenario = true;
                scenario.setStatus(ScenarioStatus.FAILED);
            } else {
                scenario.setStatus(reachedFailedScenario ? ScenarioStatus.DRAFT : ScenarioStatus.PASSED);
            }

            for (Step step : orderedSteps) {
                seen++;
                boolean isFailedStep = isFailedScenario && (
                    (failedScenarioName != null && failedStepIndex != null && step.getSequenceOrder() == failedStepIndex)
                    || (failedScenarioName == null && seen == failedGlobalStep)
                );

                if (isFailedStep) {
                    step.setStatus(StepStatus.FAILED);
                } else if (seen <= globalCompleted) {
                    step.setStatus(StepStatus.PASSED);
                } else {
                    step.setStatus(StepStatus.PENDING);
                }
            }
        }
    }

    private int toInteger(Object value, int fallback) {
        Integer parsed = toInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public FeatureResponse importGherkinAsFeature(String gherkinText) {
        // Very basic parsing for demonstration. 
        // A real implementation would use a Gherkin parser library.
        Feature feature = new Feature();
        feature.setGherkinContent(gherkinText);
        
        String[] lines = gherkinText.split("\n");
        Scenario currentScenario = null;
        int scenarioOrder = 0;
        int stepOrder = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Feature:")) {
                feature.setName(line.substring("Feature:".length()).trim());
            } else if (line.startsWith("Scenario:")) {
                if (currentScenario != null) {
                    feature.getScenarios().add(currentScenario);
                }
                currentScenario = new Scenario();
                currentScenario.setName(line.substring("Scenario:".length()).trim());
                currentScenario.setFeature(feature);
                currentScenario.setSequenceOrder(++scenarioOrder);
                stepOrder = 0;
            } else if (currentScenario != null && !line.isEmpty()) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    try {
                        StepType type = StepType.valueOf(parts[0]);
                        Step step = new Step();
                        step.setType(type);
                        step.setText(parts[1]);
                        step.setScenario(currentScenario);
                        step.setSequenceOrder(++stepOrder);
                        currentScenario.getSteps().add(step);
                    } catch (IllegalArgumentException e) {
                        // Not a recognized step type, ignore or handle
                    }
                }
            }
        }
        if (currentScenario != null) {
            feature.getScenarios().add(currentScenario);
        }
        
        if (feature.getName() == null) {
            feature.setName("Imported Feature");
        }
        
        feature = featureRepository.save(feature);
        return mapToResponse(feature);
    }

    private FeatureResponse mapToResponse(Feature feature) {
        return new FeatureResponse(
            feature.getId(),
            feature.getName(),
            feature.getDescription(),
            feature.getGherkinContent(),
            feature.getStatus(),
            feature.getCreatedAt(),
            feature.getUpdatedAt(),
            feature.getCreatedBy(),
            feature.getProjectId(),
            feature.getDefaultPageId(),
            feature.getTargetMode()
        );
    }

    private ScenarioWithStepsResponse mapScenarioWithSteps(Scenario scenario) {
        List<StepResponse> stepResponses = scenario.getSteps().stream()
            .sorted(java.util.Comparator.comparingInt(step -> step.getSequenceOrder() == null ? Integer.MAX_VALUE : step.getSequenceOrder()))
            .map(step -> new StepResponse(
                step.getId(),
                scenario.getId(),
                step.getType(),
                step.getText(),
                step.getStatus(),
                step.getSequenceOrder(),
                step.getCreatedAt(),
                step.getUpdatedAt()
            )).collect(Collectors.toList());

        return new ScenarioWithStepsResponse(
            scenario.getId(),
            scenario.getFeature().getId(),
            scenario.getName(),
            scenario.getDescription(),
            scenario.getStatus(),
            scenario.getSequenceOrder(),
            scenario.getCreatedAt(),
            scenario.getUpdatedAt(),
            stepResponses
        );
    }
}


