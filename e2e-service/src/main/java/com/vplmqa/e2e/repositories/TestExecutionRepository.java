package com.vplmqa.e2e.repositories;

import com.vplmqa.e2e.entities.TestExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {
    List<TestExecution> findByFeatureId(UUID featureId);
    List<TestExecution> findByFeatureIdOrderByCreatedAtDesc(UUID featureId);
    List<TestExecution> findByScenarioId(UUID scenarioId);
}
