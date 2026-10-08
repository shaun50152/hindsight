# ADR 0012: Backtests as indexed Kubernetes Jobs

## Status

Accepted

## Context

Historical replay must scale across Kafka partitions without loading all of `decision.made` in simulation-service. Workers must not read offsets that arrive after a backtest is requested.

## Decision

- Capture **end offsets** per partition when `POST /v1/backtests` runs; shards read only `[start, end)` for assigned slices.
- Run shards as **Indexed Jobs** (`completionMode: Indexed`, `parallelism = completions = N`) with `JOB_COMPLETION_INDEX` selecting the shard.
- Workers write **idempotent** rows to `backtest_partials` keyed by `(backtest_id, shard_index)`.
- Merge runs only when **all shards succeed**; any failure or missing partial yields report status **INCOMPLETE** with failed shard indices (no silent partial merge).
- **`local` runner** executes the same shard logic in-process for demos without a cluster.
- Publish **`backtest.completed`** via transactional outbox when a report is finalized.

## Consequences

- Backtest history is bounded by Kafka retention and the captured end offsets.
- simulation-service needs Job create RBAC in production (Phase 8); tests use the fabric8 mock server.
