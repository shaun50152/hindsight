# ADR 0006: Stable SHA-256 canary bucketing

## Status

Accepted

## Context

Canary rollouts route a percentage of customers to a candidate policy version. Customers must not flip between control and canary during a stable rollout percentage, and increasing the percentage must only move customers from control to canary.

## Decision

- Compute `bucket = SHA256("canary-bucket:" + customerId) mod 100` using the first 16 hex chars as unsigned long (never `hashCode()`).
- Route to CANARY when a CANARY version exists and `bucket < canaryPct`; otherwise use ACTIVE.
- Pure function `CustomerBucket.bucket0to99` with jqwik properties and a mutation-proof test using random buckets.

## Consequences

- Same customerId and pct always yield the same version assignment.
- Raising pct monotonically expands the canary cohort.
