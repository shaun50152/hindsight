package dev.hindsight.decision.shadow;

import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.decision.messaging.DecisionTopics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("shadow")
@ConditionalOnProperty(name = "hindsight.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ShadowDecisionMadeConsumer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ShadowDecisionService shadowDecisionService;
    private final boolean simulateCrash;

    public ShadowDecisionMadeConsumer(
            ShadowDecisionService shadowDecisionService,
            @Value("${hindsight.shadow.simulate-crash:false}") boolean simulateCrash) {
        this.shadowDecisionService = shadowDecisionService;
        this.simulateCrash = simulateCrash;
    }

    @KafkaListener(topics = DecisionTopics.DECISION_MADE, groupId = "${spring.kafka.consumer.group-id}")
    void onDecisionMade(String raw) throws Exception {
        if (simulateCrash) {
            throw new IllegalStateException("simulated shadow consumer crash");
        }
        EventEnvelope envelope = JSON.readValue(raw, EventEnvelope.class);
        if (!DecisionTopics.DECISION_MADE.equals(envelope.type())) {
            return;
        }
        shadowDecisionService.evaluateAndPublish(envelope);
    }
}
