package dev.hindsight.decision.guardrail;

import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.DecisionShadowPayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.decision.messaging.DecisionTopics;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("!shadow")
@ConditionalOnProperty(name = "hindsight.guardrail.enabled", havingValue = "true", matchIfMissing = true)
public class GuardrailMetricsCollector {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final GuardrailMetricsStore metricsStore;

    public GuardrailMetricsCollector(GuardrailMetricsStore metricsStore) {
        this.metricsStore = metricsStore;
    }

    @KafkaListener(
            topics = DecisionTopics.DECISION_MADE,
            groupId = "${spring.kafka.consumer.group-id}-guardrail-made")
    void onDecisionMade(String raw) throws Exception {
        EventEnvelope envelope = JSON.readValue(raw, EventEnvelope.class);
        if (!DecisionTopics.DECISION_MADE.equals(envelope.type())) {
            return;
        }
        DecisionMadePayload payload = JSON.treeToValue(envelope.payload(), DecisionMadePayload.class);
        if (payload.versionRole() == null) {
            return;
        }
        long latency = payload.latencyMs() != null ? payload.latencyMs() : 0L;
        Instant at = envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now();
        metricsStore.recordDecision(payload.policyId(), payload.versionRole(), payload.outcome(), latency, at);
    }

    @KafkaListener(
            topics = DecisionTopics.DECISION_SHADOW,
            groupId = "${spring.kafka.consumer.group-id}-guardrail-shadow")
    void onDecisionShadow(String raw) throws Exception {
        EventEnvelope envelope = JSON.readValue(raw, EventEnvelope.class);
        if (!DecisionTopics.DECISION_SHADOW.equals(envelope.type())) {
            return;
        }
        DecisionShadowPayload payload = JSON.treeToValue(envelope.payload(), DecisionShadowPayload.class);
        Instant at = envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now();
        metricsStore.recordShadowFlip(payload.policyId(), payload.shadowPolicyVersion(), payload.flipped(), at);
    }
}
