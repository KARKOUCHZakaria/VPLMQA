CREATE TABLE project_settings (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE REFERENCES projects(id),
    notification_email VARCHAR(255),
    slack_webhook TEXT,
    auto_ticket_creation BOOLEAN NOT NULL DEFAULT FALSE,
    default_severity VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
