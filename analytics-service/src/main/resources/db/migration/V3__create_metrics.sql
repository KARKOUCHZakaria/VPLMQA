CREATE TABLE project_metrics (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    metric_type VARCHAR(100) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    period VARCHAR(100) NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
