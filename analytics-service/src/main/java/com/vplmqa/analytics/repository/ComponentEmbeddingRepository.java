package com.vplmqa.analytics.repository;

import com.vplmqa.analytics.entity.ComponentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ComponentEmbeddingRepository extends JpaRepository<ComponentEmbedding, UUID> {

    List<ComponentEmbedding> findByProjectId(UUID projectId);

    @Query(value = "SELECT * FROM component_embeddings WHERE project_id = :projectId ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :topK", nativeQuery = true)
    List<ComponentEmbedding> findSimilar(@Param("queryVector") String queryVector, @Param("projectId") UUID projectId, @Param("topK") int topK);
}
