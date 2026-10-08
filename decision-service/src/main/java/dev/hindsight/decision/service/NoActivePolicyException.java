package dev.hindsight.decision.service;

public class NoActivePolicyException extends RuntimeException {

    private final String policyId;

    public NoActivePolicyException(String policyId) {
        super("No ACTIVE policy loaded for policyId=" + policyId);
        this.policyId = policyId;
    }

    public String policyId() {
        return policyId;
    }
}
