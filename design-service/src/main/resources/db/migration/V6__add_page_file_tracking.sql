-- Migration V6: Add page file tracking and Figma metadata
-- Adds fields to track page files in MinIO and Figma page IDs

ALTER TABLE pages ADD COLUMN IF NOT EXISTS figma_page_id VARCHAR(255) NULL;
ALTER TABLE pages ADD COLUMN IF NOT EXISTS file_link VARCHAR(500) NULL;
ALTER TABLE pages ADD COLUMN IF NOT EXISTS component_count INT NULL;
ALTER TABLE pages ADD COLUMN IF NOT EXISTS imported_at TIMESTAMP NULL;

-- Create index on figma_page_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_pages_figma_page_id ON pages(figma_page_id);

-- Create index on imported_at for sorting
CREATE INDEX IF NOT EXISTS idx_pages_imported_at ON pages(imported_at);
