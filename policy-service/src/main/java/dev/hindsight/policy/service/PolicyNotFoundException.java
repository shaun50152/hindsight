package dev.hindsight.policy.service;

public final class PolicyNotFoundException extends RuntimeException {

    public PolicyNotFoundException(String policyId, int version) {
        super("Policy not found: " + policyId + " v" + version);
    }
}
