package dev.hindsight.policy.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private static final RowMapper<OutboxMessage> ROW_MAPPER = OutboxRepository::mapRow;

    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void insert(String topic, String messageKey, String payloadJson) {
        jdbc.update(
                "INSERT INTO outbox (topic, message_key, payload) VALUES (?, ?, ?::jsonb)",
                topic,
                messageKey,
                payloadJson);
    }

    public List<OutboxMessage> claimBatch(int limit) {
        return jdbc.query(
                """
                SELECT id, topic, message_key, payload::text, created_at, attempts
                FROM outbox
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                ROW_MAPPER,
                limit);
    }

    public void markPublished(UUID id) {
        jdbc.update("UPDATE outbox SET published_at = ?, attempts = attempts + 1 WHERE id = ?", Timestamp.from(Instant.now()), id);
    }

    public void incrementAttempts(UUID id) {
        jdbc.update("UPDATE outbox SET attempts = attempts + 1 WHERE id = ?", id);
    }

    private static OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxMessage(
                rs.getObject("id", UUID.class),
                rs.getString("topic"),
                rs.getString("message_key"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getInt("attempts"));
    }
}
