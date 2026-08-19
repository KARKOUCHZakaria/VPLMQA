package com.vplmqa.analytics.service;

import com.vplmqa.analytics.entity.ComponentEmbedding;
import com.vplmqa.analytics.repository.ComponentEmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmbeddingService {

    private final ComponentEmbeddingRepository repository;

    public EmbeddingService(ComponentEmbeddingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ComponentEmbedding storeEmbedding(UUID componentId, UUID projectId, float[] vector) {
        ComponentEmbedding embedding = new ComponentEmbedding();
        embedding.setComponentId(componentId);
        embedding.setProjectId(projectId);
        embedding.setEmbedding(vector);
        return repository.save(embedding);
    }

    @Transactional(readOnly = true)
    public List<ComponentEmbedding> findSimilar(float[] queryVector, UUID projectId, int topK) {
        return repository.findSimilar(vectorToPgVector(queryVector), projectId, topK);
    }

    private String vectorToPgVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }
}
