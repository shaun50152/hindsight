# ADR 0009: Self-contained policy lifecycle events

## Status

Accepted

## Context

Decision-service and audit-service must compile and replay policies without calling policy-service at runtime. A compacted `policy.lifecycle` topic keyed only by `policyId` retains a single record per policy and drops historical version payloads after compaction.

## Decision

- Publish **full policy YAML** on every lifecycle event for a version, plus `schemaVersion` (currently `2`).
- Schema v2 adds optional `reason` (set on rollback, e.g. guardrail trips). v1 payloads omit `reason`; consumers treat it as null.
- Use Kafka message key **`{policyId}:{version}`** so compaction retains the latest state per version independently.
- Shared DTO: `dev.hindsight.common.events.PolicyLifecycleEvent` with required `yaml` on publish.
- Consumers compile YAML, verify `contentHash` matches `PolicyContentHash`, and refuse to load on mismatch.

## Consequences

- Event payloads are larger; acceptable for synthetic demo scale.
- decision-service rebuilds in-memory cache entirely from the topic on startup.
- audit-service stores full payloads for deterministic replay by `contentHash`.
