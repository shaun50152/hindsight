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
class AuditIdempotencyIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void duplicateEventIdCreatesOneRecord() throws Exception {
        jdbcTemplate.execute("TRUNCATE audit_log, audit_checkpoints RESTART IDENTITY");
        EventEnvelope envelope = new EventEnvelope(
                "dup-event-1",
                "decision.made",
                Instant.now(),
                "c1",
                JSON.readTree("{\"decisionId\":\"22222222-2222-2222-2222-222222222222\"}"));
        String json = JSON.writeValueAsString(envelope);
        kafkaTemplate.send("decision.made", "c1", json).get();
        kafkaTemplate.send("decision.made", "c1", json).get();

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> auditLogRepository.findByEventId("dup-event-1").isPresent());

        assertThat(auditLogRepository.findByEventId("dup-event-1")).isPresent();
        Long dupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE event_id = 'dup-event-1'", Long.class);
        assertThat(dupCount).isEqualTo(1);
    }
}
