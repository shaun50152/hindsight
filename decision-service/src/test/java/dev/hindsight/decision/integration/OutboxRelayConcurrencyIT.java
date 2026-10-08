package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.decision.messaging.OutboxRelay;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OutboxRelayConcurrencyIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxRelay outboxRelay;

    @Test
    void skipLockedPreventsDoublePublish() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO outbox (id, topic, message_key, payload)
                VALUES (?, 'decision.made', 'cust-x', '{"eventId":"e1"}'::jsonb)
                """,
                id);

        outboxRelay.relay();
        outboxRelay.relay();

        Long unpublished =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox WHERE id = ? AND published_at IS NULL", Long.class, id);
        assertThat(unpublished).isZero();
        Long attempts = jdbcTemplate.queryForObject("SELECT attempts FROM outbox WHERE id = ?", Long.class, id);
        assertThat(attempts).isGreaterThanOrEqualTo(1);
    }
}
