package dev.hindsight.backtest.evaluate;

import dev.hindsight.backtest.aggregate.BacktestHistograms;
import dev.hindsight.backtest.aggregate.BacktestPartialAggregate;
import dev.hindsight.backtest.source.DecisionRecord;
import dev.hindsight.backtest.source.DecisionSource;
import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.Outcome;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BacktestShardEvaluator {

    private BacktestShardEvaluator() {}

    public static BacktestPartialAggregate evaluate(CompiledPolicy policy, DecisionSource source, int samplingStride) {
        try (source) {
            return evaluateAll(policy, source.records().toList(), samplingStride);
        }
    }

    public static BacktestPartialAggregate evaluateAll(
            CompiledPolicy policy, Iterable<DecisionRecord> records, int samplingStride) {
        BacktestPartialAggregate acc = BacktestPartialAggregate.empty();
        long index = 0;
        for (DecisionRecord record : records) {
            if (samplingStride <= 1 || index % samplingStride == 0) {
                acc = accumulate(acc, policy, record);
            }
            index++;
        }
        return acc;
    }

    private static BacktestPartialAggregate accumulate(
            BacktestPartialAggregate acc, CompiledPolicy policy, DecisionRecord record) {
        DecisionMadePayload payload = record.payload();
        ApplicantSnapshot snapshot = toSnapshot(payload.applicant());
        Decision candidate = PolicyEvaluator.evaluate(policy, snapshot);
        Outcome recorded = Outcome.valueOf(record.recordedOutcome());
        Outcome evaluated = candidate.outcome();

        long total = acc.totalDecisions() + 1;
        long candidateApprovals = acc.candidateApprovals() + (evaluated == Outcome.APPROVE ? 1 : 0);
        long recordedApprovals = acc.recordedApprovals() + (recorded == Outcome.APPROVE ? 1 : 0);

        long[][] matrix = copyMatrix(acc.flipMatrix());
        int from = recorded.ordinal();
        int to = evaluated.ordinal();
        matrix[from][to]++;

        boolean flipped = recorded != evaluated;
        long flipCount = acc.flipCount() + (flipped ? 1 : 0);

        double exposureDelta = acc.exposureDelta();
        if (flipped && (recorded == Outcome.APPROVE || evaluated == Outcome.APPROVE)) {
            exposureDelta += payload.applicant().requestedIncrease();
        }

        Map<String, BacktestPartialAggregate.SegmentPartial> segments = new LinkedHashMap<>(acc.segments());
        String segment = payload.applicant().segment();
        BacktestPartialAggregate.SegmentPartial seg =
                segments.getOrDefault(segment, new BacktestPartialAggregate.SegmentPartial(0, 0));
        long flipsToDecline = seg.flipsToDecline() + (flipped && evaluated == Outcome.DECLINE ? 1 : 0);
        segments.put(segment, new BacktestPartialAggregate.SegmentPartial(seg.total() + 1, flipsToDecline));

        long[] utilBaseline = acc.utilizationBaselineBins().clone();
        long[] utilFlip = acc.utilizationFlipBins().clone();
        long[] ficoBaseline = acc.ficoBaselineBins().clone();
        long[] ficoFlip = acc.ficoFlipBins().clone();

        int utilBin = BacktestHistograms.utilizationBin(payload.applicant().utilization());
        int ficoBin = BacktestHistograms.ficoBin(payload.applicant().ficoBand());
        utilBaseline[utilBin]++;
        ficoBaseline[ficoBin]++;
        if (flipped) {
            utilFlip[utilBin]++;
            ficoFlip[ficoBin]++;
        }

        return new BacktestPartialAggregate(
                total,
                candidateApprovals,
                recordedApprovals,
                flipCount,
                matrix,
                exposureDelta,
                segments,
                utilBaseline,
                utilFlip,
                ficoBaseline,
                ficoFlip);
    }

    private static long[][] copyMatrix(long[][] matrix) {
        long[][] copy = new long[3][3];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(matrix[i], 0, copy[i], 0, 3);
        }
        return copy;
    }

    private static ApplicantSnapshot toSnapshot(DecisionMadePayload.ApplicantSnapshotPayload a) {
        return new ApplicantSnapshot(
                a.customerId(),
                a.currentLimit(),
                a.requestedIncrease(),
                a.utilization(),
                a.delinquencies12m(),
                a.tenureMonths(),
                a.ficoBand(),
                a.incomeBand(),
                a.incomeVerified(),
                a.segment(),
                a.asOf());
    }

}
