package dev.hindsight.policy.lifecycle;

public enum PolicyTransition {
    SUBMIT,
    APPROVE,
    REJECT,
    PROMOTE_SHADOW,
    PROMOTE_CANARY,
    PROMOTE_ACTIVE,
    RETIRE,
    ROLLBACK
}
