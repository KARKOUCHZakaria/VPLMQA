-- Migration V7: Increase page column lengths for longer names and URLs
-- Increases varchar length for name and url columns to accommodate longer Figma page names and generated URLs

-- Increase name column length from default 255 to 500
ALTER TABLE pages ALTER COLUMN name TYPE VARCHAR(500);

-- Increase url column length from default 255 to 1000
ALTER TABLE pages ALTER COLUMN url TYPE VARCHAR(1000);

-- Create index on url for faster lookups
CREATE INDEX IF NOT EXISTS idx_pages_url ON pages(url);
