package dev.hindsight.audit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.audit.persistence.AuditLogRepository;
import dev.hindsight.audit.testsupport.IntegrationTestBase;
import dev.hindsight.common.events.EventEnvelope;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class AuditMultiPartitionIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void gaplessSeqWithMultipleKafkaKeys() throws Exception {
        jdbcTemplate.execute("TRUNCATE audit_log, audit_checkpoints RESTART IDENTITY");
        for (int i = 0; i < 3; i++) {
            EventEnvelope envelope = new EventEnvelope(
                    "mp-evt-" + i,
                    "decision.made",
                    Instant.now(),
                    "c" + i,
                    JSON.readTree("{\"decisionId\":\"" + i + "\"}"));
            kafkaTemplate.send("decision.made", "customer-" + i, JSON.writeValueAsString(envelope)).get();
        }
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> auditLogRepository.countByChain("main") >= 3);
        assertThat(auditLogRepository.findByChainAndSeq("main", 1)).isPresent();
        assertThat(auditLogRepository.findByChainAndSeq("main", 2)).isPresent();
        assertThat(auditLogRepository.findByChainAndSeq("main", 3)).isPresent();
    }
}
