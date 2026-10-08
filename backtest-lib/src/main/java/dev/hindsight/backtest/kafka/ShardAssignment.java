package dev.hindsight.backtest.kafka;

public record ShardAssignment(int partition, long startOffset, long endOffsetExclusive) {}
