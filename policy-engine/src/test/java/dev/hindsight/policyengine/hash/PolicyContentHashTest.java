package dev.hindsight.policyengine.hash;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import org.junit.jupiter.api.Test;

class PolicyContentHashTest {

    private static final String BASE = """
            policyId: credit-line-increase
            version: 7
            description: Raise limit decisions
            inputs: applicant
            defaultOutcome: REFER
            rules:
              - id: R1
                when: applicant.ficoBand >= 3
                outcome: APPROVE
                reason: RC_STRONG
              - id: R2
                when: applicant.requestedIncrease > 10000
                outcome: REFER
                reason: RC_MANUAL
            """;

    @Test
    void hashUnchangedWhenYamlKeysReordered() {
        String reordered = """
                version: 7
                policyId: credit-line-increase
                defaultOutcome: REFER
                description: Raise limit decisions
                inputs: applicant
                rules:
                  - outcome: APPROVE
                    id: R1
                    reason: RC_STRONG
                    when: applicant.ficoBand >= 3
                  - when: applicant.requestedIncrease > 10000
                    id: R2
                    outcome: REFER
                    reason: RC_MANUAL
                """;
        Policy first = PolicyYamlParser.parse(BASE).policy();
        Policy second = PolicyYamlParser.parse(reordered).policy();
        assertThat(PolicyContentHash.hash(first)).isEqualTo(PolicyContentHash.hash(second));
    }

    @Test
    void hashChangesWhenRulesSwapped() {
        String swapped = """
            policyId: credit-line-increase
            version: 7
            description: Raise limit decisions
            inputs: applicant
            defaultOutcome: REFER
            rules:
              - id: R2
                when: applicant.requestedIncrease > 10000
                outcome: REFER
                reason: RC_MANUAL
              - id: R1
                when: applicant.ficoBand >= 3
                outcome: APPROVE
                reason: RC_STRONG
            """;
        Policy original = PolicyYamlParser.parse(BASE).policy();
        Policy reorderedRules = PolicyYamlParser.parse(swapped).policy();
        assertThat(PolicyContentHash.hash(original)).isNotEqualTo(PolicyContentHash.hash(reorderedRules));
    }

}
