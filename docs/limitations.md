# Known limitations

- Guardrail sliding-window metrics are held in memory only; they reset when decision-service restarts.
- Shadow consumer lag is not capped by guardrails; production remains isolated by design (see ADR 0010).
- synth-data generates synthetic applicants only; it is a load driver, not a source of truth for policies or decisions.
- Guardrail rollback requires `HINDSIGHT_GUARDRAIL_JWT` (OPS role) and reachable policy-service; misconfiguration fails closed on rollback attempts.
- Backtests read bounded slices of `decision.made` up to offsets captured at job creation; history is limited by topic retention and that snapshot.
- Backtest flip matrices, exposure deltas, segment adverse impact ratios, and PSI are **informational on synthetic data** — not a compliance or fair-lending claim.
- simulation-service uses fabric8 kubernetes-client **7.3.1+** so mock CRUD Job POST works with Spring Boot 4’s Jackson 2.21 classpath (fabric8 7.1.x + Jackson 2.19+ breaks `@JsonAnyGetter` serialization in the mock `PostHandler`; typed `Job` serialization was never broken).
