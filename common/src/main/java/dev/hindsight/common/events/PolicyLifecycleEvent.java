package dev.hindsight.common.events;

import java.time.Instant;

/**
 * Self-contained policy lifecycle event published on {@code policy.lifecycle}.
 * Kafka key: {@code policyId:version} (compacted topic).
 */
public record PolicyLifecycleEvent(
        String policyId,
        int version,
        String contentHash,
        String status,
        Integer canaryPct,
        String actor,
        Instant occurredAt,
        int schemaVersion,
        String yaml) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static String messageKey(String policyId, int version) {
        return policyId + ":" + version;
    }
}
