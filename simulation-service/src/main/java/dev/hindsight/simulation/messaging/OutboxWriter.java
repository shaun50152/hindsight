package dev.hindsight.simulation.messaging;

import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.simulation.persistence.OutboxRepository;
import org.springframework.stereotype.Component;
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
            outboxRepository.insert(topic, messageKey, JSON.writeValueAsString(envelope));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
