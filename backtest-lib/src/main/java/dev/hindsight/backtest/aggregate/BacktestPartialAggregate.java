package dev.hindsight.backtest.aggregate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializable partial shard results merged into an impact report. */
public record BacktestPartialAggregate(
        long totalDecisions,
        long candidateApprovals,
        long recordedApprovals,
        long flipCount,
        long[][] flipMatrix,
        double exposureDelta,
        Map<String, SegmentPartial> segments,
        long[] utilizationBaselineBins,
        long[] utilizationFlipBins,
        long[] ficoBaselineBins,
        long[] ficoFlipBins) {

    public static BacktestPartialAggregate empty() {
        return new BacktestPartialAggregate(
                0,
                0,
                0,
                0,
                new long[3][3],
                0.0,
                new LinkedHashMap<>(),
                new long[BacktestHistograms.UTILIZATION_BIN_COUNT],
                new long[BacktestHistograms.UTILIZATION_BIN_COUNT],
                new long[BacktestHistograms.FICO_BIN_COUNT],
                new long[BacktestHistograms.FICO_BIN_COUNT]);
    }

    public record SegmentPartial(long total, long flipsToDecline) {}
}
