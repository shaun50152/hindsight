package dev.hindsight.common.events;

import java.util.Map;

/** Body of a {@code backtest.completed} event. */
public record BacktestCompletedPayload(
        String backtestId,
        String candidateContentHash,
        BacktestStatus status,
        Map<String, Object> summary) {

    public enum BacktestStatus {
        COMPLETE,
        INCOMPLETE
    }
}
