package dev.hindsight.audit.chain;

import dev.hindsight.common.audit.AuditChainHash;
import dev.hindsight.common.audit.AuditGenesis;
import dev.hindsight.common.json.CanonicalJson;
import dev.hindsight.audit.messaging.AuditTopics;
import dev.hindsight.audit.persistence.AuditLogRecord;
import dev.hindsight.audit.persistence.AuditLogRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditAppender {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String chainId;
    private final int checkpointInterval;

    public AuditAppender(
            AuditLogRepository auditLogRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${hindsight.audit.chain-id:main}") String chainId,
            @Value("${hindsight.audit.checkpoint-interval:100}") int checkpointInterval) {
        this.auditLogRepository = auditLogRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.chainId = chainId;
        this.checkpointInterval = checkpointInterval;
    }

    @Transactional
    public long append(String eventId, String eventType, JsonNode payload) {
        if (auditLogRepository.findByEventId(eventId).isPresent()) {
            return auditLogRepository.findByEventId(eventId).orElseThrow().seq();
        }
        String canonicalPayload = CanonicalJson.toCanonicalString(payload);
        JsonNode canonicalNode = JSON.readTree(canonicalPayload);

        String prevHash =
                auditLogRepository.findLatest(chainId).map(AuditLogRecord::hash).orElse(AuditGenesis.PREV_HASH);
        long seq = auditLogRepository.findLatest(chainId).map(r -> r.seq() + 1).orElse(1L);
        String hash = AuditChainHash.compute(prevHash, canonicalNode);

        AuditLogRecord record = new AuditLogRecord(
                chainId,
                seq,
                eventId,
                eventType,
                canonicalPayload,
                prevHash,
                hash,
                Instant.now());
        auditLogRepository.insert(record);

        if (seq % checkpointInterval == 0) {
            auditLogRepository.insertCheckpoint(chainId, seq, hash);
            try {
                kafkaTemplate
                        .send(
                                AuditTopics.AUDIT_CHECKPOINT,
                                chainId,
                                JSON.writeValueAsString(new CheckpointMessage(chainId, seq, hash)))
                        .get();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to publish audit checkpoint", e);
            }
        }
        return seq;
    }

    public record CheckpointMessage(String chainId, long seq, String hash) {}
}
