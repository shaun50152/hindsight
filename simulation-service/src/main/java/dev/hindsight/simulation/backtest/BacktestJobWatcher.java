package dev.hindsight.simulation.backtest;

import dev.hindsight.simulation.persistence.BacktestRepository;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BacktestJobWatcher {

    private static final Logger log = LoggerFactory.getLogger(BacktestJobWatcher.class);

    private final BacktestRepository backtestRepository;
    private final BacktestFinalizeService finalizeService;
    private final LocalRunRegistry localRunRegistry;
    private final String runnerMode;
    private final String k8sNamespace;
    private final KubernetesJobCreator kubernetesJobCreator;

    public BacktestJobWatcher(
            BacktestRepository backtestRepository,
            BacktestFinalizeService finalizeService,
            LocalRunRegistry localRunRegistry,
            @Value("${hindsight.backtest.runner}") String runnerMode,
            @Value("${hindsight.backtest.kubernetes.namespace}") String k8sNamespace,
            @Autowired(required = false) KubernetesJobCreator kubernetesJobCreator) {
        this.backtestRepository = backtestRepository;
        this.finalizeService = finalizeService;
        this.localRunRegistry = localRunRegistry;
        this.runnerMode = runnerMode;
        this.k8sNamespace = k8sNamespace;
        this.kubernetesJobCreator = kubernetesJobCreator;
    }

    @Scheduled(fixedDelayString = "${hindsight.backtest.watcher.poll-interval-ms:2000}")
    public void poll() {
        for (BacktestRepository.BacktestRow backtest : backtestRepository.listRunning()) {
            try {
                if ("local".equalsIgnoreCase(backtest.runnerMode())) {
                    watchLocal(backtest.id());
                } else if ("kubernetes".equalsIgnoreCase(backtest.runnerMode())) {
                    watchKubernetes(backtest);
                }
            } catch (Exception e) {
                log.warn("Failed watching backtest {}", backtest.id(), e);
            }
        }
    }

    private void watchLocal(UUID backtestId) {
        LocalBacktestRunner.LocalRunHandle handle = localRunRegistry.get(backtestId);
        if (handle == null || !handle.isDone()) {
            return;
        }
        List<Integer> failed = handle.collectFailedShards();
        finalizeService.finalizeBacktest(backtestId, failed);
        localRunRegistry.remove(backtestId);
    }

    private void watchKubernetes(BacktestRepository.BacktestRow backtest) {
        if (kubernetesJobCreator == null || backtest.k8sJobName() == null) {
            return;
        }
        Job job = kubernetesJobCreator.getJob(k8sNamespace, backtest.k8sJobName());
        if (job == null || job.getStatus() == null) {
            return;
        }
        JobStatus status = job.getStatus();
        if (status.getActive() != null && status.getActive() > 0) {
            return;
        }
        boolean complete = status.getConditions() != null
                && status.getConditions().stream()
                        .anyMatch(c -> "Complete".equals(c.getType()) && "True".equals(c.getStatus()));
        boolean failed = status.getConditions() != null
                && status.getConditions().stream()
                        .anyMatch(c -> "Failed".equals(c.getType()) && "True".equals(c.getStatus()));
        if (!complete && !failed) {
            return;
        }
        List<Integer> failedShards = new ArrayList<>();
        if (failed) {
            failedShards.addAll(backtestRepository.missingShardIndices(backtest.id(), backtest.shardCount()));
            if (failedShards.isEmpty()) {
                for (int i = 0; i < backtest.shardCount(); i++) {
                    failedShards.add(i);
                }
            }
        }
        finalizeService.finalizeBacktest(backtest.id(), failedShards);
    }
}
