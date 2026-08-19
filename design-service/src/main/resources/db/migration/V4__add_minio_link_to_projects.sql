-- Add link_minio column to projects table to store MinIO file paths
ALTER TABLE projects ADD COLUMN IF NOT EXISTS link_minio VARCHAR(500) NULL;
