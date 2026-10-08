# Known limitations

- Guardrail sliding-window metrics are held in memory only; they reset when decision-service restarts.
- Shadow consumer lag is not capped by guardrails; production remains isolated by design (see ADR 0010).
- synth-data generates synthetic applicants only; it is a load driver, not a source of truth for policies or decisions.
- Guardrail rollback requires `HINDSIGHT_GUARDRAIL_JWT` (OPS role) and reachable policy-service; misconfiguration fails closed on rollback attempts.
