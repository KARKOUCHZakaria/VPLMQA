ALTER TABLE pages
    ADD COLUMN IF NOT EXISTS figma_object_path TEXT,
    ADD COLUMN IF NOT EXISTS web_object_path TEXT,
    ADD COLUMN IF NOT EXISTS ml_dataset_json_path TEXT,
    ADD COLUMN IF NOT EXISTS ml_dataset_csv_path TEXT;

ALTER TABLE components
    ADD COLUMN IF NOT EXISTS test_identifier VARCHAR(255),
    ADD COLUMN IF NOT EXISTS css_selector TEXT,
    ADD COLUMN IF NOT EXISTS xpath TEXT,
    ADD COLUMN IF NOT EXISTS object_path TEXT,
    ADD COLUMN IF NOT EXISTS raw_json TEXT;

CREATE INDEX IF NOT EXISTS idx_components_test_identifier ON components(test_identifier);
CREATE INDEX IF NOT EXISTS idx_components_page_source ON components(page_id, source);
