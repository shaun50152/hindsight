package dev.hindsight.backtest.aggregate;

/** Fixed bin edges shared by worker, merger, and fixture tests. */
public final class BacktestHistograms {

    public static final int UTILIZATION_BIN_COUNT = 4;
    public static final double[] UTILIZATION_EDGES = {0.0, 0.25, 0.5, 0.75, 1.0};

    public static final int FICO_BIN_COUNT = 5;

    private BacktestHistograms() {}

    public static int utilizationBin(double utilization) {
        double u = Math.max(0.0, Math.min(1.0, utilization));
        for (int i = 0; i < UTILIZATION_BIN_COUNT; i++) {
            if (u < UTILIZATION_EDGES[i + 1]) {
                return i;
            }
        }
        return UTILIZATION_BIN_COUNT - 1;
    }

    public static int ficoBin(int ficoBand) {
        int band = Math.max(1, Math.min(5, ficoBand));
        return band - 1;
    }
}
