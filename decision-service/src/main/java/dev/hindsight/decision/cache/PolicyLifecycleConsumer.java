package dev.hindsight.decision.cache;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.decision.messaging.DecisionTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "hindsight.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PolicyLifecycleConsumer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PolicyCache policyCache;

    public PolicyLifecycleConsumer(PolicyCache policyCache) {
        this.policyCache = policyCache;
    }

    @KafkaListener(
            topics = DecisionTopics.POLICY_LIFECYCLE,
            groupId = "${spring.kafka.consumer.group-id}-lifecycle")
    void onLifecycle(String payload) throws Exception {
        PolicyLifecycleEvent event = JSON.readValue(payload, PolicyLifecycleEvent.class);
        policyCache.apply(event);
    }
}
