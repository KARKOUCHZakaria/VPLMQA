package com.vplmqa.e2e.services;

import com.vplmqa.e2e.dto.CreateTestExecutionRequest;
import com.vplmqa.e2e.dto.TestExecutionResponse;
import com.vplmqa.e2e.dto.UpdateTestExecutionRequest;
import com.vplmqa.e2e.entities.ExecutionStatus;
import com.vplmqa.e2e.entities.Feature;
import com.vplmqa.e2e.entities.Scenario;
import com.vplmqa.e2e.entities.TestExecution;
import com.vplmqa.e2e.repositories.FeatureRepository;
import com.vplmqa.e2e.repositories.ScenarioRepository;
import com.vplmqa.e2e.repositories.TestExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TestExecutionService {

    private final TestExecutionRepository testExecutionRepository;
    private final FeatureRepository featureRepository;
    private final ScenarioRepository scenarioRepository;

    public TestExecutionService(TestExecutionRepository testExecutionRepository,
                                FeatureRepository featureRepository,
                                ScenarioRepository scenarioRepository) {
        this.testExecutionRepository = testExecutionRepository;
        this.featureRepository = featureRepository;
        this.scenarioRepository = scenarioRepository;
    }

    public TestExecutionResponse recordTestExecution(CreateTestExecutionRequest request) {
        Feature feature = featureRepository.findById(request.featureId())
            .orElseThrow(() -> new RuntimeException("Feature not found"));

        Scenario scenario = null;
        if (request.scenarioId() != null) {
            scenario = scenarioRepository.findById(request.scenarioId())
                .orElse(null);
        }

        TestExecution execution = new TestExecution();
        execution.setFeature(feature);
        execution.setScenario(scenario);
        
        if (request.status() != null) {
            try {
                execution.setStatus(ExecutionStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                execution.setStatus(ExecutionStatus.PENDING);
            }
        } else {
            execution.setStatus(ExecutionStatus.PENDING);
        }

        execution = testExecutionRepository.save(execution);
        return mapToResponse(execution);
    }

    public TestExecutionResponse updateExecutionResults(UUID id, UpdateTestExecutionRequest request) {
        TestExecution execution = testExecutionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("TestExecution not found"));

        if (request.status() != null) {
            try {
                execution.setStatus(ExecutionStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {}
        }
        if (request.outputLog() != null) execution.setOutputLog(request.outputLog());
        if (request.errorMessage() != null) execution.setErrorMessage(request.errorMessage());
        if (request.executionTimeMs() != null) execution.setExecutionTimeMs(request.executionTimeMs());
        if (request.screenshots() != null) execution.setScreenshots(request.screenshots());

        execution = testExecutionRepository.save(execution);
        return mapToResponse(execution);
    }

    public List<TestExecutionResponse> getExecutionHistory(UUID featureId, int limit) {
        // limit could be implemented with Pageable, keeping simple for now
        return testExecutionRepository.findByFeatureIdOrderByCreatedAtDesc(featureId).stream()
            .limit(limit)
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public TestExecutionResponse getExecution(UUID id) {
        TestExecution execution = testExecutionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("TestExecution not found"));
        return mapToResponse(execution);
    }

    public List<TestExecutionResponse> findAll() {
        return testExecutionRepository.findAll().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private TestExecutionResponse mapToResponse(TestExecution execution) {
        return new TestExecutionResponse(
            execution.getId(),
            execution.getFeature().getId(),
            execution.getScenario() != null ? execution.getScenario().getId() : null,
            execution.getStatus(),
            execution.getExecutionTimeMs(),
            execution.getOutputLog(),
            execution.getErrorMessage(),
            execution.getScreenshots(),
            execution.getCreatedAt()
        );
    }
}
