package dev.hindsight.policy.api.dto;

import dev.hindsight.policy.lifecycle.PolicyStatus;
import java.time.Instant;

public record PolicyVersionSummaryResponse(
        String policyId,
        int version,
        String contentHash,
        PolicyStatus status,
        String authorId,
        Instant createdAt,
        Instant approvedAt) {}
