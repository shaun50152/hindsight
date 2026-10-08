# ADR 0008: Single writer per audit chain

## Status

Accepted

## Context

Gapless monotonic `seq` assignment requires a total order of appends. Multiple writers would race on `max(seq)+1` unless using distributed locks or DB sequences per writer.

## Decision

- One Kafka consumer container with `concurrency = 1` ingests all audit-bound topics for a deployment.
- Configurable `hindsight.audit.chain-id` (default `main`) scopes the chain.
- Scale throughput by partitioning event types across chains in future deployments; this demo uses one chain.

## Consequences

- Simple correct seq allocation in a single JVM consumer thread.
- Multi-partition Kafka ingress is ordered by consumer poll order, not partition order.
