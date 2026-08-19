package com.vplmqa.e2e.repositories;

import com.vplmqa.e2e.entities.Step;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepRepository extends JpaRepository<Step, UUID> {
    List<Step> findByScenarioId(UUID scenarioId);
    List<Step> findByScenarioIdOrderBySequenceOrderAscCreatedAtAsc(UUID scenarioId);
    void deleteByScenarioId(UUID scenarioId);
}
