package dev.hindsight.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Body of a {@code decision.made} event (also nested in {@link EventEnvelope#payload()}). */
public record DecisionMadePayload(
        String decisionId,
        String requestId,
        String policyId,
        int policyVersion,
        String contentHash,
        String outcome,
        List<String> reasonCodes,
        BigDecimal maxIncrease,
        List<RuleTraceEntry> rulesEvaluated,
        ApplicantSnapshotPayload applicant) {

    public record RuleTraceEntry(String ruleId, boolean whenResult) {}

    public record ApplicantSnapshotPayload(
            String customerId,
            double currentLimit,
            double requestedIncrease,
            double utilization,
            int delinquencies12m,
            int tenureMonths,
            int ficoBand,
            String incomeBand,
            boolean incomeVerified,
            String segment,
            Instant asOf) {}
}
