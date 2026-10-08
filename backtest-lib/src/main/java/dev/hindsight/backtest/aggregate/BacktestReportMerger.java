package dev.hindsight.backtest.aggregate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BacktestReportMerger {

    private BacktestReportMerger() {}

    public static MergedBacktestReport merge(List<BacktestPartialAggregate> partials) {
        BacktestPartialAggregate merged = BacktestPartialAggregate.empty();
        for (BacktestPartialAggregate partial : partials) {
            merged = add(merged, partial);
        }
        double recordedApprovalRate =
                merged.totalDecisions() == 0 ? 0.0 : (double) merged.recordedApprovals() / merged.totalDecisions();
        double candidateApprovalRate =
                merged.totalDecisions() == 0 ? 0.0 : (double) merged.candidateApprovals() / merged.totalDecisions();
        double approvalRateDelta = candidateApprovalRate - recordedApprovalRate;

        double utilizationPsi = PsiCalculator.psi(merged.utilizationBaselineBins(), merged.utilizationFlipBins());
        double ficoPsi = PsiCalculator.psi(merged.ficoBaselineBins(), merged.ficoFlipBins());

        Map<String, SegmentReport> segmentReports = new LinkedHashMap<>();
        for (Map.Entry<String, BacktestPartialAggregate.SegmentPartial> entry : merged.segments().entrySet()) {
            BacktestPartialAggregate.SegmentPartial seg = entry.getValue();
            double adverseRatio = seg.total() == 0 ? 0.0 : (double) seg.flipsToDecline() / seg.total();
            segmentReports.put(
                    entry.getKey(),
                    new SegmentReport(seg.total(), seg.flipsToDecline(), adverseRatio, "informational_synthetic"));
        }

        return new MergedBacktestReport(
                merged.totalDecisions(),
                merged.flipCount(),
                merged.flipMatrix(),
                approvalRateDelta,
                merged.exposureDelta(),
                segmentReports,
                utilizationPsi,
                ficoPsi,
                merged.utilizationBaselineBins(),
                merged.utilizationFlipBins(),
                merged.ficoBaselineBins(),
                merged.ficoFlipBins());
    }

    private static BacktestPartialAggregate add(BacktestPartialAggregate a, BacktestPartialAggregate b) {
        long[][] matrix = new long[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matrix[i][j] = a.flipMatrix()[i][j] + b.flipMatrix()[i][j];
            }
        }
        Map<String, BacktestPartialAggregate.SegmentPartial> segments = new LinkedHashMap<>(a.segments());
        for (Map.Entry<String, BacktestPartialAggregate.SegmentPartial> entry : b.segments().entrySet()) {
            segments.merge(entry.getKey(), entry.getValue(), (left, right) -> new BacktestPartialAggregate.SegmentPartial(
                    left.total() + right.total(), left.flipsToDecline() + right.flipsToDecline()));
        }
        return new BacktestPartialAggregate(
                a.totalDecisions() + b.totalDecisions(),
                a.candidateApprovals() + b.candidateApprovals(),
                a.recordedApprovals() + b.recordedApprovals(),
                a.flipCount() + b.flipCount(),
                matrix,
                a.exposureDelta() + b.exposureDelta(),
                segments,
                addBins(a.utilizationBaselineBins(), b.utilizationBaselineBins()),
                addBins(a.utilizationFlipBins(), b.utilizationFlipBins()),
                addBins(a.ficoBaselineBins(), b.ficoBaselineBins()),
                addBins(a.ficoFlipBins(), b.ficoFlipBins()));
    }

    private static long[] addBins(long[] left, long[] right) {
        long[] result = new long[left.length];
        for (int i = 0; i < left.length; i++) {
            result[i] = left[i] + right[i];
        }
        return result;
    }

    public record MergedBacktestReport(
            long totalDecisions,
            long flipCount,
            long[][] flipMatrix,
            double approvalRateDelta,
            double exposureDelta,
            Map<String, SegmentReport> segments,
            double utilizationPsi,
            double ficoPsi,
            long[] utilizationBaselineBins,
            long[] utilizationFlipBins,
            long[] ficoBaselineBins,
            long[] ficoFlipBins) {}

    public record SegmentReport(long total, long flipsToDecline, double adverseImpactRatio, String label) {}
}
