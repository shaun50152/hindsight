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
        ApplicantSnapshotPayload applicant,
        Integer schemaVersion,
        Long latencyMs,
        VersionRole versionRole) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    /** Schema version when {@code schemaVersion} was omitted (legacy payloads). */
    public int schemaVersionOrDefault() {
        return schemaVersion != null ? schemaVersion : 1;
    }

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
