package dev.hindsight.backtest.source;

import java.util.stream.Stream;

public interface DecisionSource extends AutoCloseable {

    Stream<DecisionRecord> records();

    @Override
    default void close() {}
}
