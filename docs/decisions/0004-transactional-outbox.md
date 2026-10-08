# ADR 0004: Transactional outbox for policy lifecycle events

## Status

Accepted

## Context

Policy lifecycle changes must be published to Kafka (`policy.lifecycle`, compacted, key = `policyId:version`) without dual-write races between Postgres and the broker.

## Decision

- Persist outbound messages in `policy.outbox` in the **same database transaction** as `policies` updates and `policy_events` inserts.
- A scheduled **OutboxRelay** claims rows with `SELECT … FOR UPDATE SKIP LOCKED`, publishes via `KafkaTemplate`, and sets `published_at` only after send succeeds.
- Failed sends increment `attempts` and leave the row unpublished for retry (at-least-once publish; consumers must be idempotent).
- Topic creation uses a compacted `NewTopic` bean for local/dev (3 partitions, RF 1).

## Consequences

- Broker unavailability does not roll back committed policy state; relay catches up when Kafka is healthy.
- Duplicate publishes are possible on crash after send but before `published_at`; compacted topic + idempotent consumers tolerate retries.
