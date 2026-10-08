package dev.hindsight.policy.service;

public final class PolicyConflictException extends RuntimeException {

    public PolicyConflictException(String message) {
        super(message);
    }
}
