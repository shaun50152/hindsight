package dev.hindsight.simulation.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record CreateBacktestRequest(
        @NotBlank String candidateContentHash,
        List<Integer> partitions,
        Map<Integer, OffsetRange> offsetRanges,
        SamplingOptions sampling,
        Integer shardCount,
        String runner) {

    public record OffsetRange(long startOffset, Long endOffsetExclusive) {}

    public record SamplingOptions(Integer stride) {}
}
