CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    figma_file_url VARCHAR(1024),
    figma_token_encrypted TEXT,
    base_url VARCHAR(1024),
    status VARCHAR(50) NOT NULL,
    created_by UUID NOT NULL,
    organization_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
