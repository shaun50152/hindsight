# ADR 0010: Shadow isolation

## Status

Accepted

## Context

Shadow re-evaluates production decisions under a SHADOW policy version asynchronously. Production POST `/v1/decisions` must not depend on shadow health, lag, or failures (invariant 6).

## Decision

- Run shadow as Spring profile `shadow` in the same `decision-service` codebase: `web-application-type: none`, Kafka consumer group `decision-service-shadow`, no REST API beans.
- Shadow consumes `decision.made`, re-evaluates using in-memory `PolicyCache` SHADOW version, publishes `decision.shadow` via transactional outbox only (no writes to production `decisions` table).
- Idempotency: `shadow_processed(decision_id)` dedupes replays.
- Integration test documents p99 tolerance as baseline p99 + 25 ms with scenarios: shadow running, shadow crash loop, shadow lag.

## Consequences

- Shadow failure modes surface in consumer lag and DLT, not in production latency or outcomes.
- Operators run shadow as a separate Deployment/process with profile `shadow`.
