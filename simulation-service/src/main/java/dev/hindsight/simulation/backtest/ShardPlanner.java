package dev.hindsight.simulation.backtest;

import dev.hindsight.backtest.kafka.ShardAssignment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShardPlanner {

    private ShardPlanner() {}

    public static List<ShardPlanEntry> plan(int shardCount, Map<Integer, PartitionRange> ranges) {
        List<WorkUnit> units = new ArrayList<>();
        for (Map.Entry<Integer, PartitionRange> entry : ranges.entrySet()) {
            int partition = entry.getKey();
            PartitionRange range = entry.getValue();
            long span = Math.max(0, range.endOffsetExclusive() - range.startOffset());
            if (span == 0) {
                continue;
            }
            units.add(new WorkUnit(partition, range.startOffset(), range.endOffsetExclusive(), span));
        }
        List<ShardPlanEntry> shards = new ArrayList<>();
        for (int i = 0; i < shardCount; i++) {
            shards.add(new ShardPlanEntry(i, new ArrayList<>()));
        }
        if (units.isEmpty()) {
            return shards;
        }
        long total = units.stream().mapToLong(WorkUnit::span).sum();
        long[] targets = new long[shardCount];
        long assigned = 0;
        for (int i = 0; i < shardCount; i++) {
            if (i == shardCount - 1) {
                targets[i] = total - assigned;
            } else {
                targets[i] = total / shardCount;
                assigned += targets[i];
            }
        }
        int shardIndex = 0;
        long shardRemaining = targets[0];
        for (WorkUnit unit : units) {
            long cursor = unit.startOffset();
            long remainingInUnit = unit.span();
            while (remainingInUnit > 0) {
                if (shardRemaining == 0 && shardIndex < shardCount - 1) {
                    shardIndex++;
                    shardRemaining = targets[shardIndex];
                }
                long take = Math.min(remainingInUnit, Math.max(shardRemaining, 1));
                long end = cursor + take;
                shards.get(shardIndex)
                        .assignments()
                        .add(new ShardAssignment(unit.partition(), cursor, end));
                cursor = end;
                remainingInUnit -= take;
                shardRemaining -= take;
            }
        }
        return shards;
    }

    public record PartitionRange(long startOffset, long endOffsetExclusive) {}

    public record ShardPlanEntry(int shardIndex, List<ShardAssignment> assignments) {}

    private record WorkUnit(int partition, long startOffset, long endOffsetExclusive, long span) {}
}
