package dev.hindsight.policy.api.dto;

import dev.hindsight.policy.lifecycle.PolicyStatus;
import java.time.Instant;

public record PolicyVersionResponse(
        String policyId,
        int version,
        String contentHash,
        PolicyStatus status,
        String authorId,
        String approverId,
        Integer canaryPct,
        Instant createdAt,
        Instant approvedAt,
        String yaml) {}
