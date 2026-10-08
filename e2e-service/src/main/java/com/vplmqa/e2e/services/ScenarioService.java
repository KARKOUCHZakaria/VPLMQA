package com.vplmqa.e2e.services;

import com.vplmqa.e2e.dto.CreateScenarioRequest;
import com.vplmqa.e2e.dto.ScenarioResponse;
import com.vplmqa.e2e.dto.ScenarioWithStepsResponse;
import com.vplmqa.e2e.dto.StepResponse;
import com.vplmqa.e2e.dto.UpdateScenarioRequest;
import com.vplmqa.e2e.entities.Feature;
import com.vplmqa.e2e.entities.Scenario;
import com.vplmqa.e2e.entities.ScenarioStatus;
import com.vplmqa.e2e.repositories.FeatureRepository;
import com.vplmqa.e2e.repositories.ScenarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final FeatureRepository featureRepository;

    public ScenarioService(ScenarioRepository scenarioRepository, FeatureRepository featureRepository) {
        this.scenarioRepository = scenarioRepository;
        this.featureRepository = featureRepository;
    }

    public synchronized ScenarioResponse createScenario(UUID featureId, CreateScenarioRequest request) {
        Feature feature = featureRepository.findById(featureId)
            .orElseThrow(() -> new RuntimeException("Feature not found"));

        Scenario duplicate = scenarioRepository.findByFeatureId(featureId).stream()
            .filter(candidate -> java.util.Objects.equals(candidate.getSequenceOrder(), request.sequenceOrder()))
            .filter(candidate -> normalize(candidate.getName()).equals(normalize(request.name())))
            .findFirst()
            .orElse(null);
        if (duplicate != null) {
            return mapToResponse(duplicate);
        }

        Scenario scenario = new Scenario();
        scenario.setFeature(feature);
        scenario.setName(request.name());
        scenario.setDescription(request.description());
        scenario.setSequenceOrder(request.sequenceOrder());
        scenario.setStatus(ScenarioStatus.DRAFT);

        scenario = scenarioRepository.save(scenario);
        return mapToResponse(scenario);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
    public ScenarioResponse updateScenario(UUID id, UpdateScenarioRequest request) {
        Scenario scenario = scenarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Scenario not found"));

        if (request.name() != null) scenario.setName(request.name());
        if (request.description() != null) scenario.setDescription(request.description());
        if (request.sequenceOrder() != null) scenario.setSequenceOrder(request.sequenceOrder());
        if (request.status() != null) {
            try {
                scenario.setStatus(ScenarioStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {}
        }

        scenario = scenarioRepository.save(scenario);
        return mapToResponse(scenario);
    }

    public void deleteScenario(UUID id) {
        scenarioRepository.deleteById(id);
    }

    public List<ScenarioResponse> listScenariosForFeature(UUID featureId) {
        return scenarioRepository.findByFeatureId(featureId).stream()
            .sorted(java.util.Comparator.comparingInt(scenario -> scenario.getSequenceOrder() == null ? Integer.MAX_VALUE : scenario.getSequenceOrder()))
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public ScenarioWithStepsResponse getScenarioWithSteps(UUID id) {
        Scenario scenario = scenarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Scenario not found"));

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

    private ScenarioResponse mapToResponse(Scenario scenario) {
        return new ScenarioResponse(
            scenario.getId(),
            scenario.getFeature().getId(),
            scenario.getName(),
            scenario.getDescription(),
            scenario.getStatus(),
            scenario.getSequenceOrder(),
            scenario.getCreatedAt(),
            scenario.getUpdatedAt()
        );
    }
}


