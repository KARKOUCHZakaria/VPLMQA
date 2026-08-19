CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    permissions JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO roles (id, name, permissions, created_at) VALUES
    (gen_random_uuid(), 'TESTER', '{}'::jsonb, NOW()),
    (gen_random_uuid(), 'QUALITY_MANAGER', '{}'::jsonb, NOW()),
    (gen_random_uuid(), 'ADMIN', '{}'::jsonb, NOW());
