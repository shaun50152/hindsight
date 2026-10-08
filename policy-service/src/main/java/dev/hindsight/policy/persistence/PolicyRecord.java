package dev.hindsight.policy.persistence;

import dev.hindsight.policy.lifecycle.PolicyStatus;
import java.time.Instant;
import java.util.Optional;

public record PolicyRecord(
        String policyId,
        int version,
        String contentHash,
        String yaml,
        PolicyStatus status,
        String authorId,
        Optional<String> approverId,
        Optional<Integer> canaryPct,
        Instant createdAt,
        Optional<Instant> approvedAt) {}
