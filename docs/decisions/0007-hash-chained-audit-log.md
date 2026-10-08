# ADR 0007: Hash-chained audit log

## Status

Accepted

## Context

Audit records must detect tampering of stored payloads or hash links without trusting the database alone.

## Decision

- Each record has gapless `seq` per `chain_id`, unique `event_id`, canonical JSON `payload`, `prev_hash`, and `hash`.
- Genesis `prev_hash` is 64 hex zeros (`AuditGenesis.PREV_HASH`).
- `hash = SHA-256(prev_hash || canonical(payload))` where concatenation is UTF-8 string concat (see `AuditChainHash`).
- Payloads are normalized with `CanonicalJson` before persist and verify.

## Consequences

- Verify recomputes the chain and reports the first broken seq with `HASH_MISMATCH`, `PREV_HASH_MISMATCH`, or `GAP`.
