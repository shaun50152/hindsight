package dev.hindsight.decision.guardrail;

public record GuardrailBreach(
        String policyId,
        int canaryVersion,
        GuardrailMetric metric,
        double observed,
        double threshold,
        String fingerprint) {

    public enum GuardrailMetric {
        APPROVAL_RATE_DELTA,
        SHADOW_FLIP_RATE,
        ERROR_RATE,
        P99_LATENCY_MS
    }
}
