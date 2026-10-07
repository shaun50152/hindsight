# Hindsight

Policy-as-code decisioning with a time machine. Versioned decision rules, maker-checker approvals, shadow and canary rollouts with auto-rollback, a tamper-evident audit log, and deterministic replay. Built on Java 21, Spring Boot, Kafka, Postgres, and Kubernetes.

> Status: in progress. All data is synthetic. This is a demonstration project, not a compliance claim.

Full design: [docs/HINDSIGHT_BUILD_SPEC.md](docs/HINDSIGHT_BUILD_SPEC.md)

## Quickstart (dev)

```
make up      # Kafka, Postgres, Redis
make test
```

(More coming as phases land.)
