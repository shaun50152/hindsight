package dev.hindsight.policyengine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.RuleTraceEntry;
import dev.hindsight.policyengine.testsupport.ApplicantSnapshots;
import dev.hindsight.policyengine.testsupport.ValidCompiledPolicies;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.Optional;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class PolicyEngineProperties {

    @Property
    void evaluatingSameSnapshotTwiceIsDeterministic(
            @ForAll("compiledPolicy") CompiledPolicy policy, @ForAll("applicantSnapshot") ApplicantSnapshot applicant) {
        Decision first = PolicyEvaluator.evaluate(policy, applicant);
        Decision second = PolicyEvaluator.evaluate(policy, applicant);
        assertThat(second).isEqualTo(first);
    }

    @Property
    void yamlKeyReorderDoesNotChangeHash(@ForAll("yamlKeyReorderPair") YamlPair pair) {
        assertThat(PolicyContentHash.hash(pair.second())).isEqualTo(PolicyContentHash.hash(pair.first()));
    }

    @Property
    void swappingRulesChangesHash(@ForAll("ruleSwapPair") YamlPair pair) {
        assertThat(PolicyContentHash.hash(pair.second())).isNotEqualTo(PolicyContentHash.hash(pair.first()));
    }

    @Property
    void decidingRuleIsFirstMatchingRule(
            @ForAll("compiledPolicy") CompiledPolicy policy, @ForAll("applicantSnapshot") ApplicantSnapshot applicant) {
        Decision decision = PolicyEvaluator.evaluate(policy, applicant);
        Optional<String> expected = decision.trace().stream()
                .filter(RuleTraceEntry::whenResult)
                .map(RuleTraceEntry::ruleId)
                .findFirst();
        assertThat(decision.decidingRuleId()).isEqualTo(expected);
    }

    @Provide
    Arbitrary<CompiledPolicy> compiledPolicy() {
        return ValidCompiledPolicies.compiledPolicy();
    }

    @Provide
    Arbitrary<ApplicantSnapshot> applicantSnapshot() {
        return ApplicantSnapshots.applicantSnapshot();
    }

    @Provide
    Arbitrary<YamlPair> yamlKeyReorderPair() {
        String canonical = """
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: applicant.ficoBand >= 1
                    outcome: REFER
                    reason: RC_MANUAL
                """;
        String reordered = """
                version: 1
                inputs: applicant
                policyId: p
                rules:
                  - reason: RC_MANUAL
                    when: applicant.ficoBand >= 1
                    id: R1
                    outcome: REFER
                defaultOutcome: REFER
                description: d
                """;
        return Arbitraries.just(new YamlPair(
                PolicyYamlParser.parse(canonical).policy(), PolicyYamlParser.parse(reordered).policy()));
    }

    @Provide
    Arbitrary<YamlPair> ruleSwapPair() {
        String original = """
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: applicant.ficoBand >= 1
                    outcome: REFER
                    reason: RC_MANUAL
                  - id: R2
                    when: applicant.ficoBand >= 2
                    outcome: DECLINE
                    reason: RC_DELINQ
                """;
        String swapped = """
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R2
                    when: applicant.ficoBand >= 2
                    outcome: DECLINE
                    reason: RC_DELINQ
                  - id: R1
                    when: applicant.ficoBand >= 1
                    outcome: REFER
                    reason: RC_MANUAL
                """;
        return Arbitraries.just(new YamlPair(
                PolicyYamlParser.parse(original).policy(), PolicyYamlParser.parse(swapped).policy()));
    }

    record YamlPair(dev.hindsight.policyengine.model.Policy first, dev.hindsight.policyengine.model.Policy second) {}
}
