package dev.hindsight.backtest.aggregate;

/** Population Stability Index for fixed histogram bins. */
public final class PsiCalculator {

    private static final double EPS = 1e-6;

    private PsiCalculator() {}

    public static double psi(long[] baseline, long[] current) {
        if (baseline.length != current.length) {
            throw new IllegalArgumentException("Histogram length mismatch");
        }
        long baselineTotal = sum(baseline);
        long currentTotal = sum(current);
        if (baselineTotal == 0 || currentTotal == 0) {
            return 0.0;
        }
        double psi = 0.0;
        for (int i = 0; i < baseline.length; i++) {
            double expectedPct = (baseline[i] + EPS) / (baselineTotal + EPS * baseline.length);
            double actualPct = (current[i] + EPS) / (currentTotal + EPS * current.length);
            psi += (actualPct - expectedPct) * Math.log(actualPct / expectedPct);
        }
        return psi;
    }

    private static long sum(long[] bins) {
        long total = 0;
        for (long bin : bins) {
            total += bin;
        }
        return total;
    }
}
