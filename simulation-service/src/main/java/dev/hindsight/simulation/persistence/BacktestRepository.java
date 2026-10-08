package dev.hindsight.simulation.persistence;

import dev.hindsight.simulation.backtest.BacktestStatus;
import dev.hindsight.simulation.backtest.ShardPlanner;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class BacktestRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public BacktestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void insert(
            UUID id,
            String candidateContentHash,
            String policyId,
            BacktestStatus status,
            int shardCount,
            List<ShardPlanner.ShardPlanEntry> shardPlan,
            Map<Integer, Long> endOffsets,
            int samplingStride,
            String runnerMode,
            String k8sJobName) {
        jdbc.update(
                """
                INSERT INTO backtests (id, candidate_content_hash, policy_id, status, shard_count, shard_plan,
                    end_offsets, sampling_stride, runner_mode, k8s_job_name)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
                id,
                candidateContentHash,
                policyId,
                status.name(),
                shardCount,
                JSON.writeValueAsString(shardPlan),
                JSON.writeValueAsString(endOffsets),
                samplingStride,
                runnerMode,
                k8sJobName);
    }

    public void updateStatus(UUID id, BacktestStatus status) {
        jdbc.update("UPDATE backtests SET status = ?, updated_at = now() WHERE id = ?", status.name(), id);
    }

    public Optional<BacktestRow> find(UUID id) {
        List<BacktestRow> rows = jdbc.query("SELECT * FROM backtests WHERE id = ?", this::map, id);
        return rows.stream().findFirst();
    }

    public List<BacktestRow> listRunning() {
        return jdbc.query("SELECT * FROM backtests WHERE status = 'RUNNING'", this::map);
    }

    public int countPartials(UUID backtestId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM backtest_partials WHERE backtest_id = ?", Integer.class, backtestId);
        return count == null ? 0 : count;
    }

    public List<Integer> missingShardIndices(UUID backtestId, int shardCount) {
        List<Integer> present = jdbc.query(
                "SELECT shard_index FROM backtest_partials WHERE backtest_id = ?",
                (rs, rowNum) -> rs.getInt(1),
                backtestId);
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            if (!present.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    private BacktestRow map(ResultSet rs, int rowNum) throws SQLException {
        return new BacktestRow(
                rs.getObject("id", UUID.class),
                rs.getString("candidate_content_hash"),
                rs.getString("policy_id"),
                BacktestStatus.valueOf(rs.getString("status")),
                rs.getInt("shard_count"),
                rs.getString("shard_plan"),
                rs.getString("end_offsets"),
                rs.getInt("sampling_stride"),
                rs.getString("runner_mode"),
                rs.getString("k8s_job_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    public record BacktestRow(
            UUID id,
            String candidateContentHash,
            String policyId,
            BacktestStatus status,
            int shardCount,
            String shardPlanJson,
            String endOffsetsJson,
            int samplingStride,
            String runnerMode,
            String k8sJobName,
            Instant createdAt,
            Instant updatedAt) {

        public List<ShardPlanner.ShardPlanEntry> shardPlan() {
            return JSON.readValue(shardPlanJson, new TypeReference<>() {});
        }

        public Map<Integer, Long> endOffsets() {
            return JSON.readValue(endOffsetsJson, new TypeReference<>() {});
        }
    }
}
