package dev.hindsight.policy.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PolicyRuleDiffServiceTest {

    private final PolicyRuleDiffService diffService = new PolicyRuleDiffService();

    @Test
    void detectsAddedRemovedChangedAndReordered() {
        String left = """
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: A
                    when: "true"
                    outcome: APPROVE
                    reason: RC_STRONG
                  - id: B
                    when: "true"
                    outcome: DECLINE
                    reason: RC_DELINQ
                """;
        String right = """
                policyId: p
                version: 2
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: B
                    when: "false"
                    outcome: DECLINE
                    reason: RC_DELINQ
                  - id: A
                    when: "true"
                    outcome: APPROVE
                    reason: RC_STRONG
                  - id: C
                    when: "true"
                    outcome: REFER
                    reason: RC_MANUAL
                """;
        var diff = diffService.diff(left, right);
        assertThat(diff.added()).containsExactly("C");
        assertThat(diff.removed()).isEmpty();
        assertThat(diff.changed()).containsExactly("B");
        assertThat(diff.reordered()).contains("A");
    }
}
