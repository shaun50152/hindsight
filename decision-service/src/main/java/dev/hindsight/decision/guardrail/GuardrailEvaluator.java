package dev.hindsight.decision.guardrail;

import dev.hindsight.common.events.VersionRole;
import dev.hindsight.decision.guardrail.GuardrailBreach.GuardrailMetric;
import dev.hindsight.decision.guardrail.GuardrailProperties.Thresholds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure guardrail evaluation from sliding-window snapshots. */
public final class GuardrailEvaluator {

    private GuardrailEvaluator() {}

    public static List<GuardrailBreach> evaluate(
            String policyId,
            int canaryVersion,
            int minSampleSize,
            Thresholds thresholds,
            GuardrailMetricsStore store,
            Instant now) {
        List<GuardrailBreach> breaches = new ArrayList<>();

        var control = store.approvalSnapshot(policyId, VersionRole.CONTROL, now);
        var canary = store.approvalSnapshot(policyId, VersionRole.CANARY, now);
        if (control.total() >= minSampleSize && canary.total() >= minSampleSize) {
            double delta = Math.abs(canary.rate() - control.rate());
            if (delta > thresholds.getMaxApprovalRateDelta()) {
                breaches.add(new GuardrailBreach(
                        policyId,
                        canaryVersion,
                        GuardrailMetric.APPROVAL_RATE_DELTA,
                        delta,
                        thresholds.getMaxApprovalRateDelta(),
                        fingerprint(policyId, GuardrailMetric.APPROVAL_RATE_DELTA, canaryVersion, windowEpoch(now))));
            }
        }

        var errors = store.errorSnapshot(policyId, now);
        if (errors.total() >= minSampleSize) {
            double rate = errors.rate();
            if (rate > thresholds.getMaxErrorRate()) {
                breaches.add(new GuardrailBreach(
                        policyId,
                        canaryVersion,
                        GuardrailMetric.ERROR_RATE,
                        rate,
                        thresholds.getMaxErrorRate(),
                        fingerprint(policyId, GuardrailMetric.ERROR_RATE, canaryVersion, windowEpoch(now))));
            }
        }

        var latency = store.latencySnapshot(policyId, now);
        if (latency.total() >= minSampleSize) {
            long p99 = latency.p99();
            if (p99 > thresholds.getMaxP99LatencyMs()) {
                breaches.add(new GuardrailBreach(
                        policyId,
                        canaryVersion,
                        GuardrailMetric.P99_LATENCY_MS,
                        p99,
                        thresholds.getMaxP99LatencyMs(),
                        fingerprint(policyId, GuardrailMetric.P99_LATENCY_MS, canaryVersion, windowEpoch(now))));
            }
        }

        var shadow = store.shadowFlipSnapshot(policyId, canaryVersion, now);
        if (shadow.total() >= minSampleSize) {
            double flipRate = shadow.rate();
            if (flipRate > thresholds.getMaxShadowFlipRate()) {
                breaches.add(new GuardrailBreach(
                        policyId,
                        canaryVersion,
                        GuardrailMetric.SHADOW_FLIP_RATE,
                        flipRate,
                        thresholds.getMaxShadowFlipRate(),
                        fingerprint(policyId, GuardrailMetric.SHADOW_FLIP_RATE, canaryVersion, windowEpoch(now))));
            }
        }

        return breaches;
    }

    public static Optional<GuardrailBreach> firstActionable(List<GuardrailBreach> breaches) {
        return breaches.stream().findFirst();
    }

    static String fingerprint(String policyId, GuardrailMetric metric, int canaryVersion, long windowEpoch) {
        return policyId + "|" + metric.name() + "|" + canaryVersion + "|" + windowEpoch;
    }

    private static long windowEpoch(Instant now) {
        return now.getEpochSecond() / 300;
    }
}
