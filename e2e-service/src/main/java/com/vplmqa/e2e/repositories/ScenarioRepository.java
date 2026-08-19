package com.vplmqa.e2e.repositories;

import com.vplmqa.e2e.entities.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {
    List<Scenario> findByFeatureId(UUID featureId);
    void deleteByFeatureId(UUID featureId);
}
