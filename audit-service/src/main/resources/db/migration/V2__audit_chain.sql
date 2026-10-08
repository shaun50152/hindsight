CREATE TABLE audit_log (
    chain_id    VARCHAR(64)  NOT NULL,
    seq         BIGINT       NOT NULL,
    event_id    VARCHAR(255) NOT NULL UNIQUE,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL,
    prev_hash   VARCHAR(64)  NOT NULL,
    hash        VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (chain_id, seq)
);

CREATE INDEX audit_log_chain_seq_idx ON audit_log (chain_id, seq);

CREATE TABLE audit_checkpoints (
    chain_id   VARCHAR(64) NOT NULL,
    seq        BIGINT      NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chain_id, seq)
);
