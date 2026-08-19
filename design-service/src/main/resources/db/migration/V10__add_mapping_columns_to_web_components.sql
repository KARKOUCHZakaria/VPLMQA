-- Migration V10: Add mapping columns to web_components
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS mapped_figma_component_id BIGINT;
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS figma_node_id_reference VARCHAR(255);
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS functional_role VARCHAR(255);
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS test_identifier VARCHAR(255);
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS component_description TEXT;
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS figma_component_type VARCHAR(255);
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS mapping_status VARCHAR(50);
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS mapped_at TIMESTAMP;
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS last_comparison_at TIMESTAMP;
ALTER TABLE web_components ADD COLUMN IF NOT EXISTS mapping_notes TEXT;
