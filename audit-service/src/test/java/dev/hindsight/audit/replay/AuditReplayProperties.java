package dev.hindsight.audit.replay;

import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class AuditReplayProperties {

    @Property
    void evaluateTwiceMatchesStoredOutcome(@ForAll @IntRange(min = 1, max = 5) int ficoBand) {
        String yaml =
                """
                policyId: prop-p
                version: 1
                description: t
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "applicant.ficoBand >= 3"
                    outcome: APPROVE
                    reason: RC_STRONG
                """;
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        var compiled = PolicyCompiler.compileValidatedPolicy(policy).compiled().orElseThrow();
        ApplicantSnapshot snapshot = new ApplicantSnapshot(
                "c-prop", 5000, 100, 0.2, 0, 24, ficoBand, "M", true, "A", Instant.parse("2020-01-01T00:00:00Z"));
        var first = PolicyEvaluator.evaluate(compiled, snapshot);
        var second = PolicyEvaluator.evaluate(compiled, snapshot);
        org.assertj.core.api.Assertions.assertThat(first.outcome()).isEqualTo(second.outcome());
        org.assertj.core.api.Assertions.assertThat(PolicyContentHash.hash(policy)).isNotBlank();
    }
}
