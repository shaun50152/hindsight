package dev.hindsight.policy.testsupport;

public final class TestPolicyYaml {

    private TestPolicyYaml() {}

    public static String minimal(String policyId, int version) {
        return """
                policyId: %s
                version: %d
                description: test policy
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "applicant.ficoBand >= 1"
                    outcome: APPROVE
                    reason: RC_STRONG
                """
                .formatted(policyId, version);
    }
}
