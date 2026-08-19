ALTER TABLE features ADD COLUMN project_id UUID;
ALTER TABLE features ADD COLUMN default_page_id UUID;
ALTER TABLE features ADD COLUMN target_mode VARCHAR(20) NOT NULL DEFAULT 'EXTERNAL';

CREATE INDEX idx_features_project_id ON features(project_id);

CREATE TABLE step_function_registry (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    intent_signature VARCHAR(500) NOT NULL,
    intent_hash VARCHAR(64) NOT NULL,
    project_id UUID,
    function_name VARCHAR(255) NOT NULL,
    artifact_bucket VARCHAR(255) NOT NULL,
    artifact_key VARCHAR(1000) NOT NULL,
    input_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    requires_secret BOOLEAN NOT NULL DEFAULT FALSE,
    validation_evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(intent_hash, project_id, version)
);

CREATE INDEX idx_step_registry_lookup ON step_function_registry(intent_hash, project_id, status);
