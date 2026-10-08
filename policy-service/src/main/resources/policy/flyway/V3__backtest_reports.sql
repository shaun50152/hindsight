CREATE TABLE backtest_reports (
    content_hash  VARCHAR(64) PRIMARY KEY,
    backtest_id   UUID         NOT NULL UNIQUE,
    status        VARCHAR(32)  NOT NULL,
    summary       JSONB        NOT NULL DEFAULT '{}',
    completed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
