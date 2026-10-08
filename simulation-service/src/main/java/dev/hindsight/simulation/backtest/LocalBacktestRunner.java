package dev.hindsight.simulation.backtest;

import dev.hindsight.simulation.persistence.BacktestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalBacktestRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalBacktestRunner.class);

    private final BacktestShardExecutor shardExecutor;
    private final BacktestRepository backtestRepository;

    public LocalBacktestRunner(BacktestShardExecutor shardExecutor, BacktestRepository backtestRepository) {
        this.shardExecutor = shardExecutor;
        this.backtestRepository = backtestRepository;
    }

    public LocalRunHandle start(UUID backtestId) {
        BacktestRepository.BacktestRow backtest = backtestRepository
                .find(backtestId)
                .orElseThrow(() -> new IllegalStateException("Backtest not found " + backtestId));
        ExecutorService executor = Executors.newFixedThreadPool(backtest.shardCount());
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < backtest.shardCount(); i++) {
            int shardIndex = i;
            tasks.add(() -> {
                shardExecutor.runShard(backtest, shardIndex);
                return shardIndex;
            });
        }
        List<Future<Integer>> futures;
        try {
            futures = executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new IllegalStateException("Local backtest interrupted", e);
        }
        executor.shutdown();
        return new LocalRunHandle(backtestId, futures);
    }

    public record LocalRunHandle(UUID backtestId, List<Future<Integer>> futures) {

        public boolean isDone() {
            return futures.stream().allMatch(Future::isDone);
        }

        public List<Integer> collectFailedShards() {
            List<Integer> failed = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Future<Integer> future = futures.get(i);
                try {
                    future.get();
                } catch (Exception e) {
                    log.warn("Local shard {} failed for backtest {}", i, backtestId, e);
                    failed.add(i);
                }
            }
            return failed;
        }
    }
}
