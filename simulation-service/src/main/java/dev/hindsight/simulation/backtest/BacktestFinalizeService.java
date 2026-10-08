package dev.hindsight.simulation.backtest;

import dev.hindsight.backtest.aggregate.BacktestReportMerger;
import dev.hindsight.common.events.BacktestCompletedPayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.SimulationTopics;
import dev.hindsight.simulation.messaging.OutboxWriter;
import dev.hindsight.simulation.persistence.BacktestPartialRepository;
import dev.hindsight.simulation.persistence.BacktestReportRepository;
import dev.hindsight.simulation.persistence.BacktestRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

@Service
public class BacktestFinalizeService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BacktestRepository backtestRepository;
    private final BacktestPartialRepository partialRepository;
    private final BacktestReportRepository reportRepository;
    private final OutboxWriter outboxWriter;

    public BacktestFinalizeService(
            BacktestRepository backtestRepository,
            BacktestPartialRepository partialRepository,
            BacktestReportRepository reportRepository,
            OutboxWriter outboxWriter) {
        this.backtestRepository = backtestRepository;
        this.partialRepository = partialRepository;
        this.reportRepository = reportRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void finalizeBacktest(UUID backtestId, List<Integer> failedShards) {
        BacktestRepository.BacktestRow backtest = backtestRepository
                .find(backtestId)
                .orElseThrow();
        if (backtest.status() != BacktestStatus.RUNNING) {
            return;
        }
        List<Integer> failures = new ArrayList<>(failedShards);
        int partialCount = backtestRepository.countPartials(backtestId);
        if (failures.isEmpty() && partialCount < backtest.shardCount()) {
            failures.addAll(backtestRepository.missingShardIndices(backtestId, backtest.shardCount()));
        }
        ArrayNode failedNode = JSON.createArrayNode();
        failures.forEach(failedNode::add);

        if (!failures.isEmpty()) {
            backtestRepository.updateStatus(backtestId, BacktestStatus.INCOMPLETE);
            reportRepository.save(backtestId, BacktestStatus.INCOMPLETE, null, failedNode);
            publishCompleted(backtest, BacktestCompletedPayload.BacktestStatus.INCOMPLETE, summaryIncomplete(failures));
            return;
        }

        var merged = BacktestReportMerger.merge(partialRepository.loadAll(backtestId));
        JsonNode report = JSON.valueToTree(merged);
        backtestRepository.updateStatus(backtestId, BacktestStatus.COMPLETE);
        reportRepository.save(backtestId, BacktestStatus.COMPLETE, report, JSON.createArrayNode());
        publishCompleted(backtest, BacktestCompletedPayload.BacktestStatus.COMPLETE, summaryComplete(merged));
    }

    private Map<String, Object> summaryComplete(BacktestReportMerger.MergedBacktestReport merged) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDecisions", merged.totalDecisions());
        summary.put("flipCount", merged.flipCount());
        summary.put("approvalRateDelta", merged.approvalRateDelta());
        summary.put("exposureDelta", merged.exposureDelta());
        summary.put("utilizationPsi", merged.utilizationPsi());
        summary.put("ficoPsi", merged.ficoPsi());
        return summary;
    }

    private Map<String, Object> summaryIncomplete(List<Integer> failedShards) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("failedShards", failedShards);
        return summary;
    }

    private void publishCompleted(
            BacktestRepository.BacktestRow backtest,
            BacktestCompletedPayload.BacktestStatus status,
            Map<String, Object> summary) {
        BacktestCompletedPayload payload = new BacktestCompletedPayload(
                backtest.id().toString(), backtest.candidateContentHash(), status, summary);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                SimulationTopics.BACKTEST_COMPLETED,
                Instant.now(),
                backtest.id().toString(),
                JSON.valueToTree(payload));
        outboxWriter.enqueue(SimulationTopics.BACKTEST_COMPLETED, backtest.candidateContentHash(), envelope);
    }
}
