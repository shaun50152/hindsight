# 2. CEL for policy expressions

Status: accepted

## Context

Policy rules need typed, sandboxed conditions and small numeric expressions (`when`, optional `maxIncrease`) over a fixed applicant schema. The language must be safe to evaluate on the hot path and deterministic.

## Decision

Use [cel-java](https://github.com/cel-expr/cel-java) (`dev.cel:cel`) with a protobuf-typed `applicant` variable. Compile every expression at policy validation time; evaluate compiled programs only in the pure `PolicyEvaluator`.

Allowlist global functions to `min`, `max`, `abs`, and `size` (spec §3). Reject other function calls during validation.

Unreachable-rule detection (Phase 2) is static only: constant-false `when`, duplicate `when` after an earlier rule, and rules after a constant-true `when`. Overlap and SMT-style proofs are out of scope.

## Consequences

- Policies cannot call arbitrary Java or I/O from expressions.
- Authors must use the documented applicant field names in CEL.
- Some unreachable rules (overlapping conditions with different text) may not be flagged until runtime review.
