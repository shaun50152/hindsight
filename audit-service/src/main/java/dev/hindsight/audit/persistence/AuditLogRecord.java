package dev.hindsight.audit.persistence;

import java.time.Instant;

public record AuditLogRecord(
        String chainId,
        long seq,
        String eventId,
        String eventType,
        String payloadJson,
        String prevHash,
        String hash,
        Instant createdAt) {}
