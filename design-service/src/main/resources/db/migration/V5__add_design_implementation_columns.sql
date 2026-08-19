-- Add design implementation metadata columns to projects table
ALTER TABLE projects ADD COLUMN IF NOT EXISTS figma_project_name VARCHAR(255) NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS figma_design_file_id VARCHAR(255) NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS design_implementation_status VARCHAR(50) NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS last_design_sync_at TIMESTAMP NULL;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS is_design_synced BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for efficient querying by project name
CREATE INDEX IF NOT EXISTS idx_projects_figma_project_name ON projects(figma_project_name);
CREATE INDEX IF NOT EXISTS idx_projects_is_design_synced ON projects(is_design_synced);
