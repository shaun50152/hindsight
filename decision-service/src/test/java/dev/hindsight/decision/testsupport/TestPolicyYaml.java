package dev.hindsight.decision.testsupport;

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

    public static String declineEveryone(String policyId, int version) {
        return """
                policyId: %s
                version: %d
                description: bad canary
                inputs: applicant
                defaultOutcome: DECLINE
                rules:
                  - id: R-decline-all
                    when: "applicant.ficoBand >= 0"
                    outcome: DECLINE
                    reason: RC_DELINQ
                """
                .formatted(policyId, version);
    }

    public static String canaryMarker(String policyId, int version) {
        return """
                policyId: %s
                version: %d
                description: canary policy
                inputs: applicant
                defaultOutcome: DECLINE
                rules:
                  - id: R-canary
                    when: "applicant.ficoBand >= 0"
                    outcome: DECLINE
                    reason: RC_DELINQ
                """
                .formatted(policyId, version);
    }
}
