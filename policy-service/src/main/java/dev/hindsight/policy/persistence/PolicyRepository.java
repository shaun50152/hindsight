package dev.hindsight.policy.persistence;

import dev.hindsight.policy.lifecycle.PolicyStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyRepository {

    private static final RowMapper<PolicyRecord> ROW_MAPPER = PolicyRepository::mapRow;

    private final JdbcTemplate jdbc;

    public PolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public int nextVersion(String policyId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM policies WHERE policy_id = ?",
                Integer.class,
                policyId);
        return max + 1;
    }

    public void insert(PolicyRecord record) {
        jdbc.update(
                """
                INSERT INTO policies (policy_id, version, content_hash, yaml, status, author_id,
                    approver_id, canary_pct, created_at, approved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.policyId(),
                record.version(),
                record.contentHash(),
                record.yaml(),
                record.status().name(),
                record.authorId(),
                record.approverId().orElse(null),
                record.canaryPct().orElse(null),
                Timestamp.from(record.createdAt()),
                record.approvedAt().map(Timestamp::from).orElse(null));
    }

    public Optional<PolicyRecord> find(String policyId, int version) {
        List<PolicyRecord> rows = jdbc.query(
                "SELECT * FROM policies WHERE policy_id = ? AND version = ?",
                ROW_MAPPER,
                policyId,
                version);
        return rows.stream().findFirst();
    }

    public List<PolicyRecord> listByPolicyId(String policyId) {
        return jdbc.query(
                "SELECT * FROM policies WHERE policy_id = ? ORDER BY version DESC",
                ROW_MAPPER,
                policyId);
    }

    public Optional<PolicyRecord> findByContentHash(String contentHash) {
        List<PolicyRecord> rows = jdbc.query(
                "SELECT * FROM policies WHERE content_hash = ? LIMIT 1", ROW_MAPPER, contentHash);
        return rows.stream().findFirst();
    }

    public Optional<PolicyRecord> findActive(String policyId) {
        List<PolicyRecord> rows = jdbc.query(
                """
                SELECT * FROM policies WHERE policy_id = ? AND status = 'ACTIVE'
                ORDER BY version DESC LIMIT 1
                """,
                ROW_MAPPER,
                policyId);
        return rows.stream().findFirst();
    }

    public Optional<PolicyRecord> findPreviousActive(String policyId, int beforeVersion) {
        List<PolicyRecord> rows = jdbc.query(
                """
                SELECT * FROM policies
                WHERE policy_id = ? AND version < ? AND status = 'RETIRED'
                ORDER BY version DESC
                LIMIT 1
                """,
                ROW_MAPPER,
                policyId,
                beforeVersion);
        return rows.stream().findFirst();
    }

    public boolean updateStatus(
            String policyId,
            int version,
            PolicyStatus expectedStatus,
            PolicyStatus newStatus,
            Optional<String> approverId,
            Optional<Integer> canaryPct,
            Optional<Instant> approvedAt) {
        int updated = jdbc.update(
                """
                UPDATE policies SET status = ?, approver_id = COALESCE(?, approver_id),
                    canary_pct = ?, approved_at = COALESCE(?, approved_at)
                WHERE policy_id = ? AND version = ? AND status = ?
                """,
                newStatus.name(),
                approverId.orElse(null),
                canaryPct.orElse(null),
                approvedAt.map(Timestamp::from).orElse(null),
                policyId,
                version,
                expectedStatus.name());
        return updated == 1;
    }

    public int retireOtherActive(String policyId, int exceptVersion) {
        return jdbc.update(
                """
                UPDATE policies SET status = 'RETIRED'
                WHERE policy_id = ? AND status = 'ACTIVE' AND version <> ?
                """,
                policyId,
                exceptVersion);
    }

    private static PolicyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String approver = rs.getString("approver_id");
        int canary = rs.getInt("canary_pct");
        Timestamp approved = rs.getTimestamp("approved_at");
        return new PolicyRecord(
                rs.getString("policy_id"),
                rs.getInt("version"),
                rs.getString("content_hash"),
                rs.getString("yaml"),
                PolicyStatus.valueOf(rs.getString("status")),
                rs.getString("author_id"),
                Optional.ofNullable(approver),
                rs.wasNull() ? Optional.empty() : Optional.of(canary),
                rs.getTimestamp("created_at").toInstant(),
                approved == null ? Optional.empty() : Optional.of(approved.toInstant()));
    }
}
