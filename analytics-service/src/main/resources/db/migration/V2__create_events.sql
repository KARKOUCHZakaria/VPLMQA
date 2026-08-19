CREATE TABLE events (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB,
    occurred_at TIMESTAMPTZ NOT NULL
);
