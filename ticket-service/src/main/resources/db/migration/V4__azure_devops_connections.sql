CREATE TABLE azure_devops_connections (
    project_id UUID PRIMARY KEY,
    organization VARCHAR(255) NOT NULL,
    azure_project VARCHAR(255) NOT NULL,
    work_item_type VARCHAR(100) NOT NULL DEFAULT 'Bug',
    area_path VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE tickets ALTER COLUMN assigned_to TYPE VARCHAR(255) USING assigned_to::text;
ALTER TABLE tickets ADD COLUMN azure_work_item_id INTEGER;
ALTER TABLE tickets ADD COLUMN azure_work_item_url TEXT;
ALTER TABLE tickets ADD COLUMN azure_sync_status VARCHAR(50) NOT NULL DEFAULT 'NOT_CONFIGURED';
ALTER TABLE tickets ADD COLUMN azure_sync_error TEXT;
