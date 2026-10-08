package dev.hindsight.decision.guardrail;

import dev.hindsight.decision.service.NoActivePolicyException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!shadow")
@ConditionalOnProperty(name = "hindsight.guardrail.enabled", havingValue = "true", matchIfMissing = true)
public class DecisionErrorRecorder {

    private final GuardrailMetricsStore metricsStore;

    public DecisionErrorRecorder(GuardrailMetricsStore metricsStore) {
        this.metricsStore = metricsStore;
    }

    public void recordFailClosed(NoActivePolicyException ex) {
        metricsStore.recordError(ex.policyId(), Instant.now());
    }

    public void recordError(String policyId) {
        metricsStore.recordError(Optional.ofNullable(policyId).filter(p -> !p.isBlank()).orElse("unknown"), Instant.now());
    }
}
