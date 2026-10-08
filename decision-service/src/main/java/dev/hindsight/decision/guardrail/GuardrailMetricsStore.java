package dev.hindsight.decision.guardrail;

import dev.hindsight.common.events.VersionRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!shadow")
public class GuardrailMetricsStore {

    private final Duration windowDuration;
    private final Map<String, SlidingWindowBuffer> approvalsByKey = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowBuffer> latenciesByPolicy = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowBuffer> shadowFlipsByKey = new ConcurrentHashMap<>();
    private final Map<String, SlidingWindowBuffer> errorsByPolicy = new ConcurrentHashMap<>();

    public GuardrailMetricsStore(GuardrailProperties properties) {
        this.windowDuration = properties.getWindowDuration();
    }

    public void recordDecision(String policyId, VersionRole role, String outcome, long latencyMs, Instant at) {
        boolean approved = "APPROVE".equals(outcome);
        approvalsByKey
                .computeIfAbsent(approvalKey(policyId, role), k -> new SlidingWindowBuffer(windowDuration))
                .record(at, approved, latencyMs);
        latenciesByPolicy
                .computeIfAbsent(policyId, k -> new SlidingWindowBuffer(windowDuration))
                .record(at, true, latencyMs);
    }

    public void recordShadowFlip(String policyId, int shadowVersion, boolean flipped, Instant at) {
        shadowFlipsByKey
                .computeIfAbsent(shadowKey(policyId, shadowVersion), k -> new SlidingWindowBuffer(windowDuration))
                .record(at, flipped, flipped ? 1L : 0L);
    }

    public void recordError(String policyId, Instant at) {
        errorsByPolicy
                .computeIfAbsent(policyId, k -> new SlidingWindowBuffer(windowDuration))
                .record(at, true, 1L);
    }

    public SlidingWindowBuffer.WindowSnapshot approvalSnapshot(String policyId, VersionRole role, Instant now) {
        return approvalsByKey
                .getOrDefault(approvalKey(policyId, role), new SlidingWindowBuffer(windowDuration))
                .snapshot(now);
    }

    public SlidingWindowBuffer.WindowSnapshot latencySnapshot(String policyId, Instant now) {
        return latenciesByPolicy
                .getOrDefault(policyId, new SlidingWindowBuffer(windowDuration))
                .snapshot(now);
    }

    public SlidingWindowBuffer.WindowSnapshot shadowFlipSnapshot(String policyId, int shadowVersion, Instant now) {
        return shadowFlipsByKey
                .getOrDefault(shadowKey(policyId, shadowVersion), new SlidingWindowBuffer(windowDuration))
                .snapshot(now);
    }

    public SlidingWindowBuffer.WindowSnapshot errorSnapshot(String policyId, Instant now) {
        return errorsByPolicy
                .getOrDefault(policyId, new SlidingWindowBuffer(windowDuration))
                .snapshot(now);
    }

    private static String approvalKey(String policyId, VersionRole role) {
        return policyId + ":" + role.name();
    }

    private static String shadowKey(String policyId, int shadowVersion) {
        return policyId + ":shadow:" + shadowVersion;
    }
}
