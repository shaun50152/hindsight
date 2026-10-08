package dev.hindsight.policy.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyEventRepository {

    private final JdbcTemplate jdbc;

    public PolicyEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void insert(String policyId, int version, String eventType, String actorId, String detailsJson) {
        jdbc.update(
                """
                INSERT INTO policy_events (policy_id, version, event_type, actor_id, details)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """,
                policyId,
                version,
                eventType,
                actorId,
                detailsJson);
    }

    public java.util.Optional<String> findLatestDetailsJson(String policyId, int version, String eventType) {
        var rows = jdbc.query(
                """
                SELECT details::text FROM policy_events
                WHERE policy_id = ? AND version = ? AND event_type = ?
                ORDER BY id DESC LIMIT 1
                """,
                (rs, rowNum) -> rs.getString(1),
                policyId,
                version,
                eventType);
        return rows.stream().findFirst();
    }
}
