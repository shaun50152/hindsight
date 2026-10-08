package dev.hindsight.backtest.persistence;

import dev.hindsight.backtest.aggregate.BacktestPartialAggregate;
import dev.hindsight.backtest.aggregate.BacktestPartialJson;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BacktestPartialRepository {

    private final JdbcTemplate jdbc;

    public BacktestPartialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public void upsert(UUID backtestId, int shardIndex, BacktestPartialAggregate aggregate) {
        jdbc.update(
                """
                INSERT INTO backtest_partials (backtest_id, shard_index, aggregates)
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (backtest_id, shard_index) DO UPDATE
                SET aggregates = EXCLUDED.aggregates, completed_at = now()
                """,
                backtestId,
                shardIndex,
                BacktestPartialJson.toJson(aggregate));
    }
}
