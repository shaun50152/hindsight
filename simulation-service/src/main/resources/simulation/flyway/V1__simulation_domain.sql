CREATE TABLE backtests (
    id                      UUID PRIMARY KEY,
    candidate_content_hash  VARCHAR(64)  NOT NULL,
    policy_id               VARCHAR(255) NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    shard_count             INT          NOT NULL,
    shard_plan              JSONB        NOT NULL,
    end_offsets             JSONB        NOT NULL,
    sampling_stride         INT          NOT NULL DEFAULT 1,
    runner_mode             VARCHAR(32)  NOT NULL,
    k8s_job_name            VARCHAR(255),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE backtest_partials (
    backtest_id   UUID        NOT NULL REFERENCES backtests (id) ON DELETE CASCADE,
    shard_index   INT         NOT NULL,
    aggregates    JSONB       NOT NULL,
    completed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (backtest_id, shard_index)
);

CREATE TABLE backtest_reports (
    backtest_id    UUID PRIMARY KEY REFERENCES backtests (id) ON DELETE CASCADE,
    status         VARCHAR(32) NOT NULL,
    report         JSONB,
    failed_shards  JSONB       NOT NULL DEFAULT '[]',
    merged_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic        VARCHAR(255) NOT NULL,
    message_key  VARCHAR(255) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;
