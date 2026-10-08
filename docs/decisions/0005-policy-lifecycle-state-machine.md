# ADR 0005: Policy lifecycle state machine

## Status

Accepted

## Context

Policy versions move through maker-checker approval and controlled promotion (shadow → canary → active). Illegal transitions must be rejected deterministically; approved content must not change.

## Decision

- A **single table-driven** `PolicyLifecycleStateMachine` maps `(PolicyStatus, PolicyTransition)` to the next status; all other pairs throw `InvalidPolicyTransitionException`.
- Terminal states: `REJECTED`, `RETIRED` (no outbound transitions).
- **Immutability** begins at `APPROVED`: Postgres trigger blocks `yaml` / `content_hash` updates and deletes for approved-or-beyond rows; status and canary metadata may still change.
- **Maker-checker**: application rejects self-approval; DB `CHECK` enforces `approver_id <> author_id` when approver is set.
- **Rollback** (OPS): allowed only from `SHADOW`, `CANARY`, or `ACTIVE`. Current version → `RETIRED`; if rolling back from `ACTIVE`, restore the previous active version recorded at last `PROMOTED_ACTIVE` event.

## Consequences

- REST and service layers share one transition table; exhaustive tests cover the full matrix.
- Rollback semantics depend on promotion audit details, not ad hoc version scans.
