package dev.hindsight.policy.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BacktestReportRepository {

    private final JdbcTemplate jdbc;

    public BacktestReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void upsert(String contentHash, String backtestId, String status, String summaryJson) {
        jdbc.update(
                """
                INSERT INTO backtest_reports (content_hash, backtest_id, status, summary)
                VALUES (?, ?::uuid, ?, ?::jsonb)
                ON CONFLICT (content_hash) DO UPDATE
                SET backtest_id = EXCLUDED.backtest_id,
                    status = EXCLUDED.status,
                    summary = EXCLUDED.summary,
                    completed_at = now()
                """,
                contentHash,
                backtestId,
                status,
                summaryJson);
    }

    public boolean hasCompleteReport(String contentHash) {
        Boolean exists = jdbc.queryForObject(
                """
                SELECT EXISTS(
                    SELECT 1 FROM backtest_reports
                    WHERE content_hash = ? AND status = 'COMPLETE'
                )
                """,
                Boolean.class,
                contentHash);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<String> findStatus(String contentHash) {
        return jdbc.query(
                        "SELECT status FROM backtest_reports WHERE content_hash = ?",
                        (rs, rowNum) -> rs.getString(1),
                        contentHash)
                .stream()
                .findFirst();
    }
}
