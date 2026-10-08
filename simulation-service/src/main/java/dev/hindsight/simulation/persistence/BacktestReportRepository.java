package dev.hindsight.simulation.persistence;

import dev.hindsight.simulation.backtest.BacktestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

@Repository
public class BacktestReportRepository {

    private final JdbcTemplate jdbc;

    public BacktestReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void save(UUID backtestId, BacktestStatus status, JsonNode report, JsonNode failedShards) {
        jdbc.update(
                """
                INSERT INTO backtest_reports (backtest_id, status, report, failed_shards)
                VALUES (?, ?, ?::jsonb, ?::jsonb)
                ON CONFLICT (backtest_id) DO UPDATE
                SET status = EXCLUDED.status, report = EXCLUDED.report, failed_shards = EXCLUDED.failed_shards,
                    merged_at = now()
                """,
                backtestId,
                status.name(),
                report == null ? null : report.toString(),
                failedShards.toString());
    }

    public Optional<ReportRow> find(UUID backtestId) {
        List<ReportRow> rows = jdbc.query(
                """
                SELECT backtest_id, status, report::text, failed_shards::text
                FROM backtest_reports WHERE backtest_id = ?
                """,
                (rs, rowNum) -> new ReportRow(
                        rs.getObject("backtest_id", UUID.class),
                        BacktestStatus.valueOf(rs.getString("status")),
                        rs.getString("report"),
                        rs.getString("failed_shards")),
                backtestId);
        return rows.stream().findFirst();
    }

    public record ReportRow(UUID backtestId, BacktestStatus status, String reportJson, String failedShardsJson) {}
}
