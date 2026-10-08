package dev.hindsight.policy.lifecycle;

public final class InvalidPolicyTransitionException extends RuntimeException {

    public InvalidPolicyTransitionException(PolicyStatus from, PolicyTransition action) {
        super("Invalid transition: status=" + from + ", action=" + action);
    }
}
