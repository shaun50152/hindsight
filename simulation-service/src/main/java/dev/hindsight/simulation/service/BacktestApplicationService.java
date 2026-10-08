package dev.hindsight.simulation.service;

import dev.hindsight.backtest.policy.PolicyByHashLoader;
import dev.hindsight.simulation.api.dto.CreateBacktestRequest;
import dev.hindsight.simulation.backtest.BacktestStatus;
import dev.hindsight.simulation.backtest.KafkaOffsetCapture;
import dev.hindsight.simulation.backtest.KubernetesJobCreator;
import dev.hindsight.simulation.backtest.LocalBacktestRunner;
import dev.hindsight.simulation.backtest.LocalRunRegistry;
import dev.hindsight.simulation.backtest.ShardPlanner;
import dev.hindsight.simulation.persistence.BacktestReportRepository;
import dev.hindsight.simulation.persistence.BacktestRepository;
import dev.hindsight.simulation.policy.PolicyLookupService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BacktestApplicationService {

    private final PolicyLookupService policyLookupService;
    private final BacktestRepository backtestRepository;
    private final BacktestReportRepository reportRepository;
    private final LocalBacktestRunner localBacktestRunner;
    private final LocalRunRegistry localRunRegistry;
    private final String runnerMode;
    private final int defaultShardCount;
    private final String kafkaBootstrap;
    private final String decisionTopic;
    private final KubernetesJobCreator kubernetesJobCreator;

    public BacktestApplicationService(
            PolicyLookupService policyLookupService,
            BacktestRepository backtestRepository,
            BacktestReportRepository reportRepository,
            LocalBacktestRunner localBacktestRunner,
            LocalRunRegistry localRunRegistry,
            @Value("${hindsight.backtest.runner}") String runnerMode,
            @Value("${hindsight.backtest.shard-count}") int defaultShardCount,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrap,
            @Value("${hindsight.backtest.decision-topic}") String decisionTopic,
            @Autowired(required = false) KubernetesJobCreator kubernetesJobCreator) {
        this.policyLookupService = policyLookupService;
        this.backtestRepository = backtestRepository;
        this.reportRepository = reportRepository;
        this.localBacktestRunner = localBacktestRunner;
        this.localRunRegistry = localRunRegistry;
        this.runnerMode = runnerMode;
        this.defaultShardCount = defaultShardCount;
        this.kafkaBootstrap = kafkaBootstrap;
        this.decisionTopic = decisionTopic;
        this.kubernetesJobCreator = kubernetesJobCreator;
    }

    public BacktestView start(CreateBacktestRequest request) {
        PolicyLookupService.PolicyRef policy = policyLookupService
                .findByContentHash(request.candidateContentHash())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown candidateContentHash"));
        PolicyByHashLoader.compileVerified(policy.yaml(), request.candidateContentHash());

        List<Integer> partitions = request.partitions() == null || request.partitions().isEmpty()
                ? List.of(0, 1, 2)
                : request.partitions();
        Map<Integer, Long> startOffsets = new HashMap<>();
        if (request.offsetRanges() != null) {
            request.offsetRanges().forEach((partition, range) -> startOffsets.put(partition, range.startOffset()));
        }
        for (Integer partition : partitions) {
            startOffsets.putIfAbsent(partition, 0L);
        }

        Map<Integer, ShardPlanner.PartitionRange> ranges =
                KafkaOffsetCapture.captureEndOffsets(kafkaBootstrap, decisionTopic, partitions, startOffsets);
        if (request.offsetRanges() != null) {
            request.offsetRanges().forEach((partition, range) -> {
                ShardPlanner.PartitionRange captured = ranges.get(partition);
                long end = range.endOffsetExclusive() != null
                        ? Math.min(range.endOffsetExclusive(), captured.endOffsetExclusive())
                        : captured.endOffsetExclusive();
                ranges.put(partition, new ShardPlanner.PartitionRange(range.startOffset(), end));
            });
        }

        int shardCount = request.shardCount() != null ? request.shardCount() : defaultShardCount;
        List<ShardPlanner.ShardPlanEntry> shardPlan = ShardPlanner.plan(shardCount, ranges);
        int samplingStride = request.sampling() != null && request.sampling().stride() != null
                ? request.sampling().stride()
                : 1;

        UUID backtestId = UUID.randomUUID();
        Map<Integer, Long> endOffsets = new HashMap<>();
        ranges.forEach((partition, range) -> endOffsets.put(partition, range.endOffsetExclusive()));

        String k8sJobName = null;
        String effectiveRunner = request.runner() != null ? request.runner() : runnerMode;
        if ("kubernetes".equalsIgnoreCase(effectiveRunner)) {
            if (kubernetesJobCreator == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Kubernetes backtest runner is not configured");
            }
            k8sJobName = kubernetesJobCreator.createJob(
                    backtestId, shardCount, request.candidateContentHash(), samplingStride);
        }

        backtestRepository.insert(
                backtestId,
                request.candidateContentHash(),
                policy.policyId(),
                BacktestStatus.RUNNING,
                shardCount,
                shardPlan,
                endOffsets,
                samplingStride,
                effectiveRunner,
                k8sJobName);

        if ("local".equalsIgnoreCase(effectiveRunner)) {
            localRunRegistry.register(localBacktestRunner.start(backtestId));
        }

        return get(backtestId);
    }

    public BacktestView get(UUID id) {
        BacktestRepository.BacktestRow backtest = backtestRepository
                .find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backtest not found"));
        BacktestReportRepository.ReportRow report = reportRepository.find(id).orElse(null);
        return new BacktestView(
                backtest.id(),
                backtest.candidateContentHash(),
                backtest.policyId(),
                backtest.status(),
                backtest.shardCount(),
                backtest.runnerMode(),
                backtest.k8sJobName(),
                report == null ? null : report.status(),
                report == null ? null : report.reportJson(),
                report == null ? null : report.failedShardsJson());
    }

    public record BacktestView(
            UUID id,
            String candidateContentHash,
            String policyId,
            BacktestStatus status,
            int shardCount,
            String runnerMode,
            String k8sJobName,
            BacktestStatus reportStatus,
            String reportJson,
            String failedShardsJson) {}
}
