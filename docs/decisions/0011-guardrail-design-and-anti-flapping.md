# ADR 0011: Guardrail design and anti-flapping

## Status

Accepted

## Context

CANARY rollouts need automatic rollback when metrics breach thresholds. Rollback must not flap or repeat for the same breach.

## Decision

- Guardrail evaluator runs on the default (hot-path) decision-service process only (`@Profile("!shadow")`).
- Sliding-window metrics in memory from Kafka: `decision.made` (approval rate by `VersionRole`, `latencyMs` p99) and `decision.shadow` (flip rate). Fail-closed/error counts from HTTP 503 handling on POST `/v1/decisions`.
- Approval-rate delta vs control is evaluated only when both cohorts meet configurable `minSampleSize`.
- Per-`policyId` thresholds with defaults; scheduled evaluation with Micrometer counters `guardrail.evaluations` and `guardrail.trips`.
- On breach while CANARY is present: POST policy-service rollback with OPS JWT and JSON reason; store reason in policy event details.
- Anti-flapping: breach fingerprint (policy + metric + canary version + window epoch) processed once; cooldown per policy after a successful rollback.

## Consequences

- Metrics windows reset on process restart (documented in limitations).
- Guardrail JWT and policy-service URL come from env/config only, never committed.
