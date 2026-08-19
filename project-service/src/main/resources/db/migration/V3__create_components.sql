CREATE TABLE components (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL REFERENCES pages(id),
    canonical_name VARCHAR(255) NOT NULL,
    semantic_role VARCHAR(255),
    functional_meaning TEXT,
    html_id VARCHAR(255),
    figma_node_id VARCHAR(255),
    source VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    css_properties JSONB,
    bounding_box JSONB,
    screenshot TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_component_canonical_page UNIQUE (canonical_name, page_id)
);
