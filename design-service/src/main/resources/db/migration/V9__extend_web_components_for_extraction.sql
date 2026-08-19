-- Migration V9: Extend web_components table for web extraction
-- Adds comprehensive columns for web component extraction with Playwright

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS component_name VARCHAR(255);

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS html_tag VARCHAR(100);

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS html_class VARCHAR(500);

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS text_content TEXT;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS attributes TEXT;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS css_selector TEXT;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS xpath TEXT;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS position_x DOUBLE PRECISION;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS position_y DOUBLE PRECISION;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS width DOUBLE PRECISION;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS height DOUBLE PRECISION;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS file_link VARCHAR(500);

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS raw_json TEXT;

ALTER TABLE web_components 
ADD COLUMN IF NOT EXISTS imported_at TIMESTAMP;

-- Create indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_web_components_component_name ON web_components(component_name);
CREATE INDEX IF NOT EXISTS idx_web_components_html_tag ON web_components(html_tag);
CREATE INDEX IF NOT EXISTS idx_web_components_file_link ON web_components(file_link);
CREATE INDEX IF NOT EXISTS idx_web_components_page_id ON web_components(page_id);

-- Add comments for clarity
COMMENT ON COLUMN web_components.component_name IS 'Extracted component name/label';
COMMENT ON COLUMN web_components.html_tag IS 'HTML element tag (button, input, div, etc.)';
COMMENT ON COLUMN web_components.html_class IS 'HTML class attribute value';
COMMENT ON COLUMN web_components.text_content IS 'Text content of the element';
COMMENT ON COLUMN web_components.attributes IS 'JSON string of all HTML attributes';
COMMENT ON COLUMN web_components.css_selector IS 'CSS selector to locate element';
COMMENT ON COLUMN web_components.xpath IS 'XPath to locate element';
COMMENT ON COLUMN web_components.position_x IS 'X coordinate of element';
COMMENT ON COLUMN web_components.position_y IS 'Y coordinate of element';
COMMENT ON COLUMN web_components.width IS 'Element width';
COMMENT ON COLUMN web_components.height IS 'Element height';
COMMENT ON COLUMN web_components.file_link IS 'Path to component JSON file in MinIO storage';
COMMENT ON COLUMN web_components.raw_json IS 'Complete component data as JSON';
COMMENT ON COLUMN web_components.imported_at IS 'Extraction timestamp';
