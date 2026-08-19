-- Migration V8: Add file_link column to figma_components
-- Adds file_link column to store MinIO paths for component JSON files

ALTER TABLE figma_components 
ADD COLUMN IF NOT EXISTS file_link VARCHAR(500);

-- Create index on file_link for faster lookups
CREATE INDEX IF NOT EXISTS idx_figma_components_file_link ON figma_components(file_link);

-- Add comment for clarity
COMMENT ON COLUMN figma_components.file_link IS 'Path to component JSON file in MinIO storage';
