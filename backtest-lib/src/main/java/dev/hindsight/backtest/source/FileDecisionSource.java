package dev.hindsight.backtest.source;

import java.util.List;
import java.util.stream.Stream;

public final class FileDecisionSource implements DecisionSource {

    private final List<DecisionRecord> records;

    public FileDecisionSource(List<DecisionRecord> records) {
        this.records = List.copyOf(records);
    }

    @Override
    public Stream<DecisionRecord> records() {
        return records.stream();
    }
}
