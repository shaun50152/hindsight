CREATE TABLE decisions (
    decision_id     UUID PRIMARY KEY,
    request_id      VARCHAR(255) NOT NULL UNIQUE,
    customer_id     VARCHAR(255) NOT NULL,
    policy_id       VARCHAR(255) NOT NULL,
    policy_version  INT          NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    outcome         VARCHAR(32)  NOT NULL,
    reason_codes    JSONB        NOT NULL DEFAULT '[]',
    max_increase    NUMERIC,
    rule_trace      JSONB        NOT NULL,
    input_snapshot  JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX decisions_customer_id_idx ON decisions (customer_id);

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
