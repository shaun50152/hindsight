package dev.hindsight.policy.messaging;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.policy.persistence.OutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxWriter {

    public static final String POLICY_LIFECYCLE_TOPIC = "policy.lifecycle";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OutboxRepository outboxRepository;

    public OutboxWriter(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void enqueueLifecycleEvent(PolicyLifecycleEvent event) {
        try {
            String payload = JSON.writeValueAsString(event);
            String key = PolicyLifecycleEvent.messageKey(event.policyId(), event.version());
            outboxRepository.insert(POLICY_LIFECYCLE_TOPIC, key, payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize lifecycle event", e);
        }
    }
}
