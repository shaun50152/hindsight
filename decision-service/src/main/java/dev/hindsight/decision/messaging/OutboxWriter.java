package dev.hindsight.decision.messaging;

import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.decision.persistence.OutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxWriter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OutboxRepository outboxRepository;

    public OutboxWriter(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void enqueue(String topic, String messageKey, EventEnvelope envelope) {
        try {
            String payload = JSON.writeValueAsString(envelope);
            outboxRepository.insert(topic, messageKey, payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
