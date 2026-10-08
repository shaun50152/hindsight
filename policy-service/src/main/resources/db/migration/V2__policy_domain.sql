-- Phase 3: policy domain tables, outbox, immutability trigger.

CREATE TABLE policies (
    policy_id   VARCHAR(255) NOT NULL,
    version     INT          NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    yaml        TEXT         NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    author_id   VARCHAR(255) NOT NULL,
    approver_id VARCHAR(255),
    canary_pct  INT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    PRIMARY KEY (policy_id, version),
    CONSTRAINT policies_maker_checker CHECK (approver_id IS NULL OR approver_id <> author_id),
    CONSTRAINT policies_canary_pct_range CHECK (canary_pct IS NULL OR (canary_pct >= 1 AND canary_pct <= 100))
);

CREATE INDEX policies_policy_id_idx ON policies (policy_id);
CREATE INDEX policies_policy_id_status_idx ON policies (policy_id, status);

CREATE TABLE policy_events (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   VARCHAR(255) NOT NULL,
    version     INT          NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    actor_id    VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    details     JSONB
);

CREATE INDEX policy_events_policy_id_idx ON policy_events (policy_id, version);

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

CREATE OR REPLACE FUNCTION policy_immutable_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.status IN ('APPROVED', 'SHADOW', 'CANARY', 'ACTIVE', 'RETIRED') THEN
        RAISE EXCEPTION 'cannot delete immutable policy version';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.status IN ('APPROVED', 'SHADOW', 'CANARY', 'ACTIVE', 'RETIRED') THEN
        IF NEW.yaml IS DISTINCT FROM OLD.yaml OR NEW.content_hash IS DISTINCT FROM OLD.content_hash THEN
            RAISE EXCEPTION 'cannot modify yaml or content_hash of immutable policy version';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER policies_immutable_trigger
    BEFORE UPDATE OR DELETE ON policies
    FOR EACH ROW
    EXECUTE FUNCTION policy_immutable_guard();
