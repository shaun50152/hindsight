package dev.hindsight.decision.guardrail;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Thread-safe sliding window of timestamped boolean/long samples. */
final class SlidingWindowBuffer {

    private final Deque<Sample> samples = new ArrayDeque<>();
    private final Duration windowDuration;

    SlidingWindowBuffer(Duration windowDuration) {
        this.windowDuration = windowDuration;
    }

    synchronized void record(Instant at, boolean flag, long value) {
        evictOld(at);
        samples.addLast(new Sample(at, flag, value));
    }

    synchronized WindowSnapshot snapshot(Instant now) {
        evictOld(now);
        int total = samples.size();
        int trueCount = 0;
        List<Long> values = new ArrayList<>(total);
        for (Sample s : samples) {
            if (s.flag()) {
                trueCount++;
            }
            values.add(s.value());
        }
        return new WindowSnapshot(total, trueCount, values);
    }

    private void evictOld(Instant now) {
        Instant cutoff = now.minus(windowDuration);
        while (!samples.isEmpty() && samples.peekFirst().at().isBefore(cutoff)) {
            samples.removeFirst();
        }
    }

    record Sample(Instant at, boolean flag, long value) {}

    record WindowSnapshot(int total, int trueCount, List<Long> values) {
        double rate() {
            return total == 0 ? 0.0 : (double) trueCount / total;
        }

        long p99() {
            if (values.isEmpty()) {
                return 0L;
            }
            List<Long> sorted = values.stream().sorted().toList();
            int index = (int) Math.ceil(0.99 * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }
    }
}
