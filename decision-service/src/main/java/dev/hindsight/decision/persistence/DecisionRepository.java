package dev.hindsight.decision.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DecisionRepository {

    private static final RowMapper<DecisionRecord> MAPPER = DecisionRepository::map;

    private final JdbcTemplate jdbc;

    public DecisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void insert(DecisionRecord record) {
        jdbc.update(
                """
                INSERT INTO decisions (
                    decision_id, request_id, customer_id, policy_id, policy_version, content_hash,
                    outcome, reason_codes, max_increase, rule_trace, input_snapshot, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?)
                """,
                record.decisionId(),
                record.requestId(),
                record.customerId(),
                record.policyId(),
                record.policyVersion(),
                record.contentHash(),
                record.outcome(),
                record.reasonCodesJson(),
                record.maxIncrease(),
                record.ruleTraceJson(),
                record.inputSnapshotJson(),
                Timestamp.from(record.createdAt()));
    }

    public Optional<DecisionRecord> findByRequestId(String requestId) {
        var list = jdbc.query("SELECT * FROM decisions WHERE request_id = ?", MAPPER, requestId);
        return list.stream().findFirst();
    }

    public Optional<DecisionRecord> findByDecisionId(UUID decisionId) {
        var list = jdbc.query("SELECT * FROM decisions WHERE decision_id = ?", MAPPER, decisionId);
        return list.stream().findFirst();
    }

    public long countByRequestId(String requestId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM decisions WHERE request_id = ?", Long.class, requestId);
        return count == null ? 0 : count;
    }

    public boolean isDuplicateKey(DuplicateKeyException e) {
        return true;
    }

    private static DecisionRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new DecisionRecord(
                rs.getObject("decision_id", UUID.class),
                rs.getString("request_id"),
                rs.getString("customer_id"),
                rs.getString("policy_id"),
                rs.getInt("policy_version"),
                rs.getString("content_hash"),
                rs.getString("outcome"),
                rs.getString("reason_codes"),
                rs.getBigDecimal("max_increase"),
                rs.getString("rule_trace"),
                rs.getString("input_snapshot"),
                rs.getTimestamp("created_at").toInstant());
    }
}
