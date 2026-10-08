package dev.hindsight.decision.shadow;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShadowProcessedRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShadowProcessedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return true if this decision was newly marked processed */
    public boolean tryMarkProcessed(UUID decisionId) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO shadow_processed (decision_id) VALUES (?) ON CONFLICT (decision_id) DO NOTHING",
                decisionId);
        return inserted > 0;
    }
}
