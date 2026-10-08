package dev.hindsight.audit.persistence;

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
public class AuditLogRepository {

    private static final RowMapper<AuditLogRecord> MAPPER = AuditLogRepository::map;

    private final JdbcTemplate jdbc;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public Optional<AuditLogRecord> findByEventId(String eventId) {
        var rows = jdbc.query("SELECT * FROM audit_log WHERE event_id = ?", MAPPER, eventId);
        return rows.stream().findFirst();
    }

    public Optional<AuditLogRecord> findByChainAndSeq(String chainId, long seq) {
        var rows = jdbc.query("SELECT * FROM audit_log WHERE chain_id = ? AND seq = ?", MAPPER, chainId, seq);
        return rows.stream().findFirst();
    }

    public Optional<AuditLogRecord> findLatest(String chainId) {
        var rows = jdbc.query(
                "SELECT * FROM audit_log WHERE chain_id = ? ORDER BY seq DESC LIMIT 1", MAPPER, chainId);
        return rows.stream().findFirst();
    }

    public List<AuditLogRecord> findRange(String chainId, long from, long to) {
        return jdbc.query(
                "SELECT * FROM audit_log WHERE chain_id = ? AND seq >= ? AND seq <= ? ORDER BY seq",
                MAPPER,
                chainId,
                from,
                to);
    }

    public void insert(AuditLogRecord record) {
        jdbc.update(
                """
                INSERT INTO audit_log (chain_id, seq, event_id, event_type, payload, prev_hash, hash, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """,
                record.chainId(),
                record.seq(),
                record.eventId(),
                record.eventType(),
                record.payloadJson(),
                record.prevHash(),
                record.hash(),
                Timestamp.from(record.createdAt()));
    }

    public void insertCheckpoint(String chainId, long seq, String hash) {
        jdbc.update(
                "INSERT INTO audit_checkpoints (chain_id, seq, hash) VALUES (?, ?, ?)",
                chainId,
                seq,
                hash);
    }

    public long countByChain(String chainId) {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE chain_id = ?", Long.class, chainId);
        return c == null ? 0 : c;
    }

    private static AuditLogRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogRecord(
                rs.getString("chain_id"),
                rs.getLong("seq"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getString("prev_hash"),
                rs.getString("hash"),
                rs.getTimestamp("created_at").toInstant());
    }
}
