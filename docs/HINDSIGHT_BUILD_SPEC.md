# Hindsight: Build Spec

A policy-as-code decisioning platform with a time machine. Versioned decision rules go through maker-checker approval, shadow and canary rollout with auto-rollback, and every decision lands in a tamper-evident audit log that can be replayed deterministically. Backtests run as Kubernetes Jobs. Deployed on Kubernetes locally (kind), in CI (kind), and on AWS (EKS).

Domain: credit line increase decisions on fully synthetic data.

One-line pitch for the README: "Git, CI/CD, and a flight recorder for automated financial decisions. Answers 'why was this customer declined, and what happens if we change the rule?'"

Stack: Java 21, Spring Boot 4, Kafka (Strimzi), Postgres (CloudNativePG), Redis, CEL (cel-java), Kubernetes, Helm, KEDA, Terraform, GitHub Actions, Micrometer, OpenTelemetry, Prometheus, Grafana, React (2 pages).

---

## 0. Rules to paste into your AI tool first

- Java 21, Spring Boot 4.1.x, Gradle multi-module. Modules: `common`, `policy-service`, `decision-service`, `audit-service`, `simulation-service`.
- Determinism is a feature: the decision function is pure. Same snapshot + same policy version = same output. No clocks, randomness, or external calls inside evaluation. Time and any randomness come in through the input snapshot.
- Every state change is an append-only event or an audited row.
- Every Kafka consumer is idempotent. Producers use the transactional outbox.
- Policy versions are immutable once approved (content hash is the identity).
- Tests ship in the same commit as the code. Money-of-the-system logic (evaluation, hashing, rollout) gets property tests.
- Small commits, conventional commit messages. Keep an `docs/bugs.md` log of real bugs you hit and how you fixed them.
- After each phase, run the full test suite and fix before moving on.
- All images run as non-root with arbitrary UID, read-only root filesystem, no hardcoded user (OpenShift compatible).

---

## 1. Repo layout

```
hindsight/
  common/                  events, DTOs, hashing utils, canonical JSON, error model
  policy-service/
  decision-service/        hot path + shadow mode (Spring profile)
  audit-service/
  simulation-service/      creates backtest Jobs, merges results
  backtest-worker/         the container the Jobs run (thin Spring Boot CLI app)
  ui/                      React + TS, 2 pages
  synth-data/              synthetic applicant generator + traffic driver
  deploy/
    helm/hindsight/        chart for all services
    kustomize/             overlays: kind, aws
    k8s-platform/          Strimzi Kafka CR, CloudNativePG cluster, KEDA ScaledObjects
    terraform/             EKS, VPC, ECR, OIDC role for GitHub Actions
    grafana/  prometheus/
    loadtest/              k6 scripts
  docs/
    architecture.md  decisions/ (ADRs)  runbook.md  bugs.md  limitations.md
  .github/workflows/
  README.md
```

---

## 2. Services

### policy-service
- Owns policies. A policy is a YAML document: metadata + ordered rules + default outcome.
- Identity: `policyId` + `version` + `contentHash` (SHA-256 of canonical JSON of the document). Approved versions are immutable (DB trigger blocks UPDATE/DELETE on approved rows).
- Lifecycle: `DRAFT -> IN_REVIEW -> APPROVED -> SHADOW -> CANARY(pct) -> ACTIVE -> RETIRED`. Allowed transitions enforced in one state machine class and tested exhaustively.
- Maker-checker: approver user id must differ from author id. Enforced in service layer and by a DB check constraint.
- Validation on submit: parse DSL, compile every CEL expression, type-check against the input schema, reject unknown fields, detect unreachable rules, reject duplicate rule ids.
- Publishes `policy.lifecycle` events (compacted topic keyed by policyId) via outbox. decision-service consumes these to know what to run.
- REST: create draft, submit for review, approve, reject, promote, rollback, list versions, diff two versions (structured rule-level diff).
- Roles: MAKER, CHECKER, OPS, AUDITOR (JWT).

### decision-service (hot path)
- `POST /v1/decisions` with an applicant snapshot. Returns `APPROVE | DECLINE | REFER`, reason codes, matched rules, policy version + hash, decisionId. Idempotent on `requestId`.
- Keeps compiled policies in memory (compiled CEL programs cached per version hash), rebuilt from the `policy.lifecycle` topic on startup.
- Rollout routing: ACTIVE version serves by default. If a CANARY exists, route `pct%` of customers using a stable hash of `customerId` (so a customer never flips between versions mid-rollout). Bucket assignment is a pure function, unit tested.
- Writes decision + outbox row in one transaction, publishes `decision.made` containing: input snapshot, policy version hash, rules fired in order, outcome. Key by `customerId`.
- **Shadow mode** (Spring profile `shadow`, separate Deployment, separate consumer group): consumes `decision.made`, re-evaluates under the SHADOW candidate, emits `decision.shadow`. Never touches the production response path.
- **Guardrails**: a scheduled evaluator reads canary metrics (approval rate delta vs control, error rate, p99 latency, flip rate vs shadow). On breach it calls policy-service rollback and emits an audit event.

### audit-service
- Consumes `decision.made`, `decision.shadow`, `policy.lifecycle`. Appends to a hash-chained log in Postgres.
- Record: `seq`, `payload` (canonical JSON), `prevHash`, `hash = SHA256(prevHash || canonical(payload))`. Single writer per chain (partition by chain id, one consumer), `seq` unique.
- Checkpoints: every N records, store a checkpoint (seq + hash) and publish it to a topic (and optionally write to S3 with Object Lock in the AWS deployment, noted as the anchor outside the DB).
- `GET /v1/audit/verify?from=&to=` recomputes the chain and returns OK or the first broken seq.
- `POST /v1/audit/replay/{decisionId}` loads the stored snapshot and the exact policy version by hash, re-evaluates using the same evaluation library, and returns match or mismatch with a diff.
- App DB role has INSERT and SELECT only on the audit tables.

### simulation-service
- `POST /v1/backtests` with candidate policy version + time window + sampling options.
- Creates a Kubernetes **indexed Job** using the fabric8 client: `completions = parallelism = N`, each pod takes a slice of partitions and offset ranges of the `decision.made` topic (or a Parquet/CSV export in object storage as fallback), evaluates the candidate vs the recorded outcome, and writes partial results.
- Merges partials into an impact report: flip matrix (approve to decline etc.), approval rate delta, exposure delta (sum of requested increases flipped), breakdown by synthetic segment, adverse impact ratio per segment (informational, labeled synthetic), PSI drift on key inputs.
- Report stored in Postgres and shown to the reviewer ("blast radius") before approval. Promotion API requires a backtest report newer than the policy's last edit.
- Job RBAC: dedicated ServiceAccount with a Role limited to creating Jobs in its own namespace. TTL after finished, resource limits set.

### ui (2 pages)
1. **Policy page**: version timeline, rule diff between versions, lifecycle controls, backtest report with the flip matrix and blast radius.
2. **Time machine page**: enter a decisionId, see the input, trace of rules fired, replay under original version (shows MATCH), replay under another version (shows diff), and an "audit verify" button with a live tamper-detection result.

---

## 3. Policy DSL

```yaml
policyId: credit-line-increase
version: 7
description: Raise limit decisions for existing cardholders
inputs: applicant            # schema defined in common
defaultOutcome: REFER
rules:
  - id: R1-delinquency
    when: applicant.delinquencies12m >= 2
    outcome: DECLINE
    reason: RC_DELINQ
  - id: R2-high-util
    when: applicant.utilization > 0.85 && applicant.tenureMonths < 12
    outcome: DECLINE
    reason: RC_UTIL_NEW
  - id: R3-strong
    when: applicant.ficoBand >= 3 && applicant.utilization < 0.5 && applicant.incomeVerified
    outcome: APPROVE
    reason: RC_STRONG
    maxIncrease: "min(applicant.currentLimit * 0.25, 5000)"
  - id: R4-large-request
    when: applicant.requestedIncrease > 10000
    outcome: REFER
    reason: RC_MANUAL
```

- First matching rule wins (documented). Rule order is part of the content hash.
- Expressions are CEL with a typed input schema. No function calls beyond a small allowlist (min, max, abs, size).
- Reason codes come from a controlled enum in `common`.

Applicant snapshot (synthetic): `customerId, currentLimit, requestedIncrease, utilization, delinquencies12m, tenureMonths, ficoBand (1-5), incomeBand, incomeVerified, segment (A/B/C/D synthetic), asOf (timestamp passed in)`.

---

## 4. Kafka topics

| Topic | Key | Notes |
|---|---|---|
| `policy.lifecycle` | policyId | compacted, source of truth for active/shadow/canary state |
| `decision.made` | customerId | retention long enough for backtests, full snapshot in payload |
| `decision.shadow` | customerId | shadow evaluations |
| `audit.checkpoint` | chainId | periodic chain checkpoints |
| `*.dlt` | same | dead letters, with a replay endpoint |

Strimzi `Kafka` + `KafkaTopic` CRs under `deploy/k8s-platform/`. 3 partitions for decision topics locally, document the sizing reasoning in an ADR.

---

## 5. Invariants (each needs an automated test, listed in the README)

1. **Deterministic replay:** for random applicants and random valid policies (jqwik), evaluating twice gives identical output, and replay from the stored record under the original version equals the stored outcome.
2. **Chain integrity:** verify passes on untouched data and fails at the exact first modified record when any byte of any stored payload or hash is altered.
3. **Immutability:** an approved policy's content hash never changes; update attempts fail at DB level.
4. **Maker-checker:** an author cannot approve their own policy (service test + DB constraint test).
5. **Canary stability:** a given customerId always maps to the same version for a given rollout percentage, and increasing the percentage only moves customers from control to canary, never back.
6. **Shadow isolation:** with shadow consumers running or crashing, production responses and latency are unchanged (test shadow failure does not affect `/decisions`).
7. **Rollback:** guardrail breach restores the previous ACTIVE version, emits an audit record, and decision-service picks it up within a bounded time.
8. **Idempotency:** same `requestId` submitted concurrently N times produces one decision record and identical responses.
9. **Backtest equivalence:** running a backtest of the *currently active* policy over history yields zero flips.

Tools: JUnit 5, Mockito, jqwik, Testcontainers (Kafka, Postgres, Redis), plus a kind-based smoke test in CI.

Include one "mutation proof" test: remove the stable bucketing (use random instead) and show invariant 5 fails. Mention it in the README.

---

## 6. Kubernetes layer

- **Local and CI:** kind cluster, `make cluster-up` installs Strimzi, CloudNativePG, KEDA, kube-prometheus-stack via Helm, then the Hindsight chart.
- **Helm chart:** one chart, per-service values, probes (readiness/liveness via Actuator), resource requests and limits, PodDisruptionBudgets, securityContext (non-root, readOnlyRootFilesystem, drop all capabilities), configurable replicas.
- **Autoscaling:**
  - decision-service: HPA on CPU (and requests per second via Prometheus adapter if you want, optional).
  - shadow Deployment and backtest workers: **KEDA ScaledObject** on Kafka consumer lag, scale to zero when idle.
- **NetworkPolicies:** default deny; only decision-service and policy-service can reach audit-service ingestion paths (via Kafka, not direct); only services can reach Postgres; UI only talks to the gateway/service endpoints.
- **RBAC:** per-service ServiceAccounts, least privilege. Only simulation-service can create Jobs.
- **Secrets:** Kubernetes Secrets locally, External Secrets or Sealed Secrets in the AWS overlay (pick one, ADR it).
- **OpenShift note:** run as arbitrary UID, no privileged ports, group-writable temp dirs. Document in README. If you get a free OpenShift Local or Developer Sandbox, deploy once and add a screenshot.
- **Backtest Jobs:** indexed Jobs with `ttlSecondsAfterFinished`, `backoffLimit`, resource limits, and a failure path (partial failures surface in the report as incomplete, not silently ignored).

---

## 7. AWS deployment (Terraform)

- `deploy/terraform/`: VPC, EKS (managed node group, 2x t3.large or Spot), ECR repos, IAM OIDC role for GitHub Actions (no long-lived keys), optional S3 bucket with Object Lock for audit checkpoints.
- Keep Kafka (Strimzi) and Postgres (CloudNativePG) in-cluster to control cost and keep one deployment model. Document the production alternative (MSK + RDS) and why you did not use it here, in an ADR.
- Pipeline: `deploy-aws.yml` triggered manually (`workflow_dispatch`): build and push images to ECR, `terraform apply`, `helm upgrade --install` with the `aws` overlay, run smoke tests against the public endpoint, publish the demo URL in the job summary.
- Cost control: AWS Budget alert, `make aws-down` runs `terraform destroy`. Keep it up only for demos and recording. Note the approximate daily cost in `docs/runbook.md`.
- Ingress via AWS Load Balancer Controller or a simple NLB. HTTPS optional.
- Honest README line: "Deployed to EKS for demo, torn down to save cost, recording linked."

---

## 8. Observability

- Micrometer metrics: decisions/sec by outcome and policy version, decision latency histogram, canary vs control approval rate, shadow flip rate, guardrail evaluations, audit append latency, chain verify duration, consumer lag, DLT depth, backtest job duration and pod count.
- Grafana dashboards as JSON: "Decision Performance", "Rollout and Guardrails", "Audit and Lag", "Backtests".
- OpenTelemetry tracing: one trace across HTTP -> outbox -> Kafka -> audit append (Jaeger or Tempo).
- Prometheus alert rules: guardrail breach, DLT depth > 0, consumer lag high, audit append failing.
- Structured JSON logs with correlation id in HTTP and Kafka headers.

---

## 9. Performance

- k6 scripts in `deploy/loadtest/`.
- Report: hardware, replica count, sustained decisions/sec, p50/p95/p99 latency, error rate. Run once on kind (laptop) and once on EKS and report both with conditions.
- Include a short before/after: one real bottleneck you found (e.g. recompiling CEL per request, DB pool size, outbox batch size), what you changed, the numbers. Real and modest beats big and unexplained.
- Show KEDA scaling shadow workers up under load and back to zero.

---

## 10. CI/CD

- `ci.yml`: build, unit tests, Testcontainers integration tests, jacoco, OWASP dependency-check, gitleaks, SpotBugs or Error Prone.
- `k8s-smoke.yml`: spin up kind, deploy chart, run smoke test (create policy, approve, decide, replay equals original, verify chain, tamper detection), tear down.
- `docker.yml`: build and push images to GHCR on main, tag by SHA.
- `deploy-aws.yml`: manual, as described above.
- **Policy CI:** a workflow that lints and tests policy YAML files in `policies/` (compile, type-check, run example fixtures). "Policy changes go through CI" is part of the story.
- Badges for build, coverage, smoke test.

---

## 11. Demos to record (short GIFs or one 3 minute video, at the top of the README)

1. **Tamper detection:** change a stored decision via SQL, run verify, it points to the exact record.
2. **Deterministic replay:** replay a decision under its original version (MATCH), then under a newer version (shows diff).
3. **Bad policy rollout:** push a deliberately bad rule to canary, guardrail trips, automatic rollback, audit trail shows it.
4. **Backtest at scale:** trigger a backtest, show N pods spinning up in parallel, then the impact report and blast radius.
5. **Maker-checker:** author tries to approve own policy, rejected.
6. **Resilience:** `kubectl delete pod` on decision-service and a Kafka broker under load, no lost or duplicated decisions, chain still verifies.
7. **Grafana + KEDA scaling** during a load run.

---

## 12. README structure

1. Pitch, hero GIF, one-line description.
2. Why this exists (regulatory questions it answers) in 3 sentences, with a note that all data is synthetic and this is a demonstration, not a compliance claim.
3. Architecture diagram and a sequence diagram of "decision -> audit -> replay" (Mermaid).
4. Quickstart: `make cluster-up && make demo` (max 3 commands).
5. Invariants table (invariant, how enforced, which test proves it).
6. Benchmarks with conditions.
7. Key decisions linking to ADRs (outbox, canonical JSON hashing, single-writer chain, in-cluster Kafka/Postgres, KEDA vs HPA, CEL vs custom DSL).
8. Failure modes and handling.
9. **Known limitations** (be specific: single chain writer is a throughput ceiling, synthetic data, no real identity provider, etc.).
10. How I used AI tools: scaffolding and test generation, what you reviewed by hand, one suggestion you rejected and why, one bug it introduced that you caught.
11. What I would do next (this is where the operator/CRD idea lives as a stated future direction, not built).

---

## 13. Build phases (give your AI tool one at a time)

**Phase 1: Foundation.** Gradle multi-module, `common` (canonical JSON, hashing, event types), Docker Compose for Kafka/Postgres/Redis for fast local dev, Flyway, Actuator, base CI.

**Phase 2: Evaluation core.** Applicant schema, DSL parser, CEL compile and type-check, pure evaluator with rule trace, jqwik determinism tests (invariant 1 part 1).

**Phase 3: policy-service.** Versioning, content hashing, lifecycle state machine, maker-checker, immutability triggers, diff endpoint, `policy.lifecycle` via outbox. Invariants 3, 4.

**Phase 4: decision-service.** Hot path, idempotency, outbox, decision snapshot events, canary bucketing, in-memory compiled policy cache from the compacted topic. Invariants 5, 8 and the mutation proof.

**Phase 5: audit-service.** Hash chain, checkpoints, verify endpoint, replay endpoint, restricted DB role. Invariants 1 (replay), 2.

**Phase 6: Shadow and guardrails.** Shadow profile and consumer group, `decision.shadow`, guardrail evaluator, rollback flow. Invariants 6, 7.

**Phase 7: Backtests.** Backtest worker, simulation-service Job creation with fabric8, partial result merge, impact report, PSI and segment metrics, promotion gate requiring a fresh report. Invariant 9.

**Phase 8: Kubernetes.** Helm chart, Strimzi and CloudNativePG manifests, KEDA, HPA, NetworkPolicies, RBAC, security contexts, kind scripts, `k8s-smoke.yml`.

**Phase 9: Observability and load.** Metrics, dashboards, tracing, alerts, k6, tuning pass with a real before/after.

**Phase 10: UI.** The two pages. Keep it clean and fast.

**Phase 11: AWS.** Terraform, ECR push, OIDC deploy, smoke test on EKS, record the demo, destroy.

**Phase 12: Polish.** Demos, README, ADRs, limitations, bugs log, runbook, diagrams, final CI green.

Optional stretch only after everything above is solid: counterfactual explanations for single-threshold numeric rules ("approved if utilization were below 0.62") with a property test that applying the suggestion flips the outcome.

---

## 14. Making it look as real as it is

- Commit continuously. Your history should show it growing in stages, including `fix:` and `refactor:` commits. Do not squash.
- Open issues and PRs against yourself for the bigger features and merge them. It shows workflow.
- Keep `docs/bugs.md` honest. Two or three real entries (e.g. "canary bucketing used `hashCode()` which changed across JVMs; switched to a stable SHA-256 based bucket").
- Every number in the README has conditions next to it.
- You must be able to explain, from memory: canonical JSON and why hashing needs it, why the audit chain has a single writer, how replay stays deterministic, how canary bucketing works, what happens if Kafka loses a broker mid-decision, why Jobs instead of in-process backtests.
- Do a mock walkthrough: open random files and explain them out loud. If you can't, read and rewrite until you can.

---

## 15. Definition of done

- [ ] `make cluster-up && make demo` works from a clean clone
- [ ] CI green, coverage badge, kind smoke test green
- [ ] All 9 invariants have passing tests, plus the mutation proof
- [ ] Deployed once to EKS, demo recorded, infrastructure destroyed, cost noted
- [ ] 7 demos recorded and embedded
- [ ] ADRs written, limitations and bugs docs present
- [ ] Benchmarks measured with conditions
- [ ] No secrets, no TODOs in main, no raw credentials in Terraform or Helm values
- [ ] You can walk any file in the repo out loud

---

## 16. Resume bullets (replace X/Y/Z with real measurements)

**Hindsight | Java 21, Spring Boot, Kafka, PostgreSQL, Redis, Kubernetes, Helm, KEDA, Terraform, AWS, GitHub Actions**
- Built a policy-as-code credit decisioning platform of 4 Spring Boot microservices over Kafka with versioned rules, maker-checker approvals, and shadow and canary rollouts that automatically roll back on guardrail breach, serving X decisions/sec at p99 latency of Y ms.
- Engineered a hash-chained, tamper-evident audit log with deterministic decision replay, verified by jqwik property tests over Z random inputs and a tamper test that pinpoints the first modified record.
- Ran historical backtests as Kubernetes indexed Jobs across N pods producing flip-matrix, exposure, drift, and segment impact reports that gate policy promotion.
- Deployed via Helm with Strimzi Kafka, KEDA lag-based autoscaling, NetworkPolicies, and least-privilege RBAC; validated in a kind cluster in GitHub Actions and provisioned on AWS EKS with Terraform and OIDC-based deploys.
