package dev.hindsight.backtest.source;

import dev.hindsight.common.events.DecisionMadePayload;

public record DecisionRecord(DecisionMadePayload payload) {

    public String recordedOutcome() {
        return payload.outcome();
    }
}
