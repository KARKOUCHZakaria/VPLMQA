package com.vplmqa.e2e.services;

import com.vplmqa.e2e.dto.CreateStepRequest;
import com.vplmqa.e2e.dto.StepResponse;
import com.vplmqa.e2e.dto.UpdateStepRequest;
import com.vplmqa.e2e.entities.Scenario;
import com.vplmqa.e2e.entities.Step;
import com.vplmqa.e2e.entities.StepStatus;
import com.vplmqa.e2e.repositories.ScenarioRepository;
import com.vplmqa.e2e.repositories.StepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class StepService {

    private final StepRepository stepRepository;
    private final ScenarioRepository scenarioRepository;

    public StepService(StepRepository stepRepository, ScenarioRepository scenarioRepository) {
        this.stepRepository = stepRepository;
        this.scenarioRepository = scenarioRepository;
    }

    public synchronized StepResponse createStep(UUID scenarioId, CreateStepRequest request) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
            .orElseThrow(() -> new RuntimeException("Scenario not found"));

        Step duplicate = stepRepository.findByScenarioId(scenarioId).stream()
            .filter(candidate -> java.util.Objects.equals(candidate.getSequenceOrder(), request.sequenceOrder()))
            .filter(candidate -> candidate.getType() == request.type())
            .filter(candidate -> normalize(candidate.getText()).equals(normalize(request.text())))
            .findFirst()
            .orElse(null);
        if (duplicate != null) {
            return mapToResponse(duplicate);
        }

        Step step = new Step();
        step.setScenario(scenario);
        step.setType(request.type());
        step.setText(request.text());
        step.setSequenceOrder(request.sequenceOrder());
        step.setStatus(StepStatus.PENDING);

        step = stepRepository.save(step);
        return mapToResponse(step);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
    public StepResponse updateStep(UUID id, UpdateStepRequest request) {
        Step step = stepRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Step not found"));

        if (request.type() != null) step.setType(request.type());
        if (request.text() != null) step.setText(request.text());
        if (request.sequenceOrder() != null) step.setSequenceOrder(request.sequenceOrder());
        if (request.status() != null) {
            try {
                step.setStatus(StepStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {}
        }

        step = stepRepository.save(step);
        return mapToResponse(step);
    }

    public void deleteStep(UUID id) {
        stepRepository.deleteById(id);
    }

    public List<StepResponse> listStepsForScenario(UUID scenarioId) {
        return stepRepository.findByScenarioIdOrderBySequenceOrderAscCreatedAtAsc(scenarioId).stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public StepResponse getStepWithDetails(UUID id) {
        Step step = stepRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Step not found"));
        return mapToResponse(step);
    }

    private StepResponse mapToResponse(Step step) {
        return new StepResponse(
            step.getId(),
            step.getScenario().getId(),
            step.getType(),
            step.getText(),
            step.getStatus(),
            step.getSequenceOrder(),
            step.getCreatedAt(),
            step.getUpdatedAt()
        );
    }
}


