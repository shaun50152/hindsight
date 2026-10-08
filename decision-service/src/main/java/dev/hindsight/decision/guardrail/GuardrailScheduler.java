package dev.hindsight.decision.guardrail;

import dev.hindsight.decision.cache.PolicyCache;
import dev.hindsight.decision.cache.PolicyCache.RoutedPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!shadow")
@ConditionalOnProperty(name = "hindsight.guardrail.enabled", havingValue = "true", matchIfMissing = true)
public class GuardrailScheduler {

    private static final Logger log = LoggerFactory.getLogger(GuardrailScheduler.class);

    private final GuardrailProperties properties;
    private final PolicyCache policyCache;
    private final GuardrailMetricsStore metricsStore;
    private final GuardrailTripState tripState;
    private final GuardrailRollbackClient rollbackClient;
    private final Counter evaluationsCounter;
    private final Counter tripsCounter;

    public GuardrailScheduler(
            GuardrailProperties properties,
            PolicyCache policyCache,
            GuardrailMetricsStore metricsStore,
            GuardrailTripState tripState,
            GuardrailRollbackClient rollbackClient,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.policyCache = policyCache;
        this.metricsStore = metricsStore;
        this.tripState = tripState;
        this.rollbackClient = rollbackClient;
        this.evaluationsCounter = Counter.builder("guardrail.evaluations").register(meterRegistry);
        this.tripsCounter = Counter.builder("guardrail.trips").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${hindsight.guardrail.evaluation-interval-ms:5000}")
    public void evaluate() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        for (String policyId : policyCache.policyIds()) {
            var route = policyCache.routeFor(policyId);
            if (route.canary().isEmpty()) {
                continue;
            }
            RoutedPolicy canary = route.canary().get();
            evaluationsCounter.increment();
            var thresholds = properties.thresholdsFor(policyId);
            List<GuardrailBreach> breaches = GuardrailEvaluator.evaluate(
                    policyId,
                    canary.version(),
                    properties.getMinSampleSize(),
                    thresholds,
                    metricsStore,
                    now);
            log.debug(
                    "Guardrail evaluation policyId={} canaryVersion={} breaches={}",
                    policyId,
                    canary.version(),
                    breaches.size());
            if (breaches.isEmpty()) {
                continue;
            }
            if (tripState.inCooldown(policyId, now)) {
                log.debug("Guardrail cooldown active policyId={}", policyId);
                continue;
            }
            var breach = GuardrailEvaluator.firstActionable(breaches).orElseThrow();
            if (tripState.alreadyTripped(breach.fingerprint())) {
                continue;
            }
            String reason = "Guardrail breach: "
                    + breach.metric().name()
                    + " observed="
                    + breach.observed()
                    + " threshold="
                    + breach.threshold();
            try {
                rollbackClient.rollback(policyId, canary.version(), reason);
                tripState.markTripped(policyId, breach.fingerprint());
                tripState.enterCooldown(policyId, now, properties.getCooldownDuration());
                tripsCounter.increment();
                log.warn(
                        "Guardrail trip policyId={} version={} metric={} observed={} threshold={}",
                        policyId,
                        canary.version(),
                        breach.metric(),
                        breach.observed(),
                        breach.threshold());
            } catch (Exception e) {
                log.error("Guardrail rollback failed policyId={} version={}: {}", policyId, canary.version(), e.getMessage());
            }
        }
    }
}
