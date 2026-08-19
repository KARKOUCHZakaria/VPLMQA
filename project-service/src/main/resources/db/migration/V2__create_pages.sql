CREATE TABLE pages (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    name VARCHAR(255) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    last_scanned_at TIMESTAMPTZ,
    scan_status VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
