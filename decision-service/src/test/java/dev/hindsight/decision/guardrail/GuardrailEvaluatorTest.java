package dev.hindsight.decision.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.common.events.VersionRole;
import dev.hindsight.decision.guardrail.GuardrailBreach.GuardrailMetric;
import dev.hindsight.decision.guardrail.GuardrailProperties.Thresholds;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GuardrailEvaluatorTest {

    @Test
    void tripsWhenApprovalRateDeltaExceedsThreshold() {
        GuardrailProperties props = new GuardrailProperties();
        props.setWindowDuration(Duration.ofMinutes(5));
        props.setMinSampleSize(5);
        GuardrailMetricsStore store = new GuardrailMetricsStore(props);
        Instant now = Instant.parse("2024-06-01T12:00:00Z");
        for (int i = 0; i < 10; i++) {
            store.recordDecision("p1", VersionRole.CONTROL, "APPROVE", 10, now);
        }
        for (int i = 0; i < 10; i++) {
            store.recordDecision("p1", VersionRole.CANARY, "DECLINE", 10, now);
        }
        Thresholds thresholds = new Thresholds();
        thresholds.setMaxApprovalRateDelta(0.10);
        var breaches = GuardrailEvaluator.evaluate("p1", 2, 5, thresholds, store, now);
        assertThat(breaches).anyMatch(b -> b.metric() == GuardrailMetric.APPROVAL_RATE_DELTA);
    }

    @Test
    void noTripBelowMinSampleSize() {
        GuardrailProperties props = new GuardrailProperties();
        props.setWindowDuration(Duration.ofMinutes(5));
        GuardrailMetricsStore store = new GuardrailMetricsStore(props);
        Instant now = Instant.now();
        store.recordDecision("p1", VersionRole.CONTROL, "APPROVE", 10, now);
        store.recordDecision("p1", VersionRole.CANARY, "DECLINE", 10, now);
        Thresholds thresholds = new Thresholds();
        thresholds.setMaxApprovalRateDelta( 0.01);
        var breaches = GuardrailEvaluator.evaluate("p1", 2, 30, thresholds, store, now);
        assertThat(breaches).isEmpty();
    }
}
