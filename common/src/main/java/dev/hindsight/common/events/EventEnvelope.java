package dev.hindsight.common.events;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * Base envelope for domain events published on Kafka.
 *
 * @param eventId       unique id for this event instance
 * @param type          event type discriminator (e.g. {@code decision.made})
 * @param occurredAt    event time from the producing snapshot (not wall-clock at consume)
 * @param correlationId ties related events across services
 * @param payload       event-specific body as JSON
 */
public record EventEnvelope(
        String eventId,
        String type,
        Instant occurredAt,
        String correlationId,
        JsonNode payload
) {}
