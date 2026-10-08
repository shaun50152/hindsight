package dev.hindsight.simulation.persistence;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
        UUID id, String topic, String messageKey, String payloadJson, Instant createdAt, int attempts) {}
