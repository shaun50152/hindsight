package dev.hindsight.backtest.persistence;

import dev.hindsight.backtest.kafka.ShardAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class BacktestShardPlanRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;

    public BacktestShardPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public ShardPlanRow load(UUID backtestId, int shardIndex) {
        String shardPlanJson = jdbc.queryForObject(
                "SELECT shard_plan FROM backtests WHERE id = ?", String.class, backtestId);
        List<ShardPlanEntry> entries = JSON.readValue(shardPlanJson, new TypeReference<>() {});
        return entries.stream()
                .filter(entry -> entry.shardIndex() == shardIndex)
                .findFirst()
                .map(entry -> new ShardPlanRow(
                        jdbc.queryForObject(
                                "SELECT sampling_stride FROM backtests WHERE id = ?", Integer.class, backtestId),
                        entry.assignments()))
                .orElseThrow(() -> new IllegalStateException("No shard plan for index " + shardIndex));
    }

    public record ShardPlanEntry(int shardIndex, List<ShardAssignment> assignments) {}

    public record ShardPlanRow(int samplingStride, List<ShardAssignment> assignments) {}
}
