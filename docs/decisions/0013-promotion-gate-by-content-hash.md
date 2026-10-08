# ADR 0013: Promotion gate by content hash

## Status

Accepted

## Context

Rollout to CANARY or ACTIVE changes production blast radius. Approved policy versions are immutable; identity is the content hash.

## Decision

- policy-service consumes **`backtest.completed`** into `backtest_reports` keyed by `content_hash`.
- Promotion to **SHADOW** does not require a backtest report.
- Promotion to **CANARY** or **ACTIVE** returns **409** unless a **COMPLETE** report exists for the version's exact `contentHash`.
- "Fresh" means a report **exists for this hash**, not a time window (hash immutability makes staleness moot).

## Consequences

- OPS must run and complete a backtest before canary/active promotion.
- Incomplete backtests do not satisfy the gate.
