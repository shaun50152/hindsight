package dev.hindsight.common.events;

import java.util.List;

/** Body of a {@code decision.shadow} event (nested in {@link EventEnvelope#payload()}). */
public record DecisionShadowPayload(
        int schemaVersion,
        String originalDecisionId,
        String policyId,
        int shadowPolicyVersion,
        String shadowContentHash,
        String originalOutcome,
        String shadowOutcome,
        List<DecisionMadePayload.RuleTraceEntry> rulesEvaluated,
        boolean flipped) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
