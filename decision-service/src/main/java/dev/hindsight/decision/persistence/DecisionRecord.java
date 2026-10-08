package dev.hindsight.decision.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DecisionRecord(
        UUID decisionId,
        String requestId,
        String customerId,
        String policyId,
        int policyVersion,
        String contentHash,
        String outcome,
        String reasonCodesJson,
        BigDecimal maxIncrease,
        String ruleTraceJson,
        String inputSnapshotJson,
        Instant createdAt) {}
