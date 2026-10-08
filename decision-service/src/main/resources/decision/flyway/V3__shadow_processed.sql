CREATE TABLE IF NOT EXISTS shadow_processed (
    decision_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
