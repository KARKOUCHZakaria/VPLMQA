CREATE TABLE retry_queue (
    id UUID PRIMARY KEY,
    delivery_log_id UUID NOT NULL REFERENCES delivery_logs(id),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
