package dev.hindsight.policyengine.model;

import dev.hindsight.common.domain.ReasonCode;
import java.util.List;
import java.util.Optional;

/** Semantic policy document used for content hashing (not raw YAML). */
public record PolicyHashDocument(
        String policyId,
        int version,
        String description,
        String inputs,
        String defaultOutcome,
        List<RuleHashDocument> rules) {

    public record RuleHashDocument(
            String id,
            String when,
            String outcome,
            String reason,
            Optional<String> maxIncrease) {
        static RuleHashDocument from(Rule rule) {
            return new RuleHashDocument(
                    rule.id(),
                    rule.when(),
                    rule.outcome().name(),
                    rule.reason().name(),
                    rule.maxIncrease());
        }
    }

    public static PolicyHashDocument from(Policy policy) {
        return new PolicyHashDocument(
                policy.policyId(),
                policy.version(),
                policy.description(),
                policy.inputs(),
                policy.defaultOutcome().name(),
                policy.rules().stream().map(RuleHashDocument::from).toList());
    }
}
