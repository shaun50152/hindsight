package dev.hindsight.policy.messaging;

import dev.hindsight.policy.lifecycle.PolicyStatus;
import java.time.Instant;

public record PolicyLifecycleEvent(
        String policyId,
        int version,
        String contentHash,
        PolicyStatus status,
        Integer canaryPct,
        String actor,
        Instant occurredAt) {}
