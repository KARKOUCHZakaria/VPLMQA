CREATE TABLE component_embeddings (
    id UUID PRIMARY KEY,
    component_id UUID NOT NULL,
    project_id UUID NOT NULL,
    embedding vector(1536),
    canonical_name VARCHAR(255),
    semantic_role VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
