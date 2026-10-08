package dev.hindsight.synthdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SyntheticApplicantGenerator {

    private static final String[] SEGMENTS = {"A", "B", "C", "D"};
    private static final int[] SEGMENT_WEIGHTS = {40, 30, 20, 10};
    private static final String[] INCOME_BANDS = {"L", "M", "H"};

    private SyntheticApplicantGenerator() {}

    public static List<SyntheticApplicant> generate(int count, long seed) {
        Random random = new Random(seed);
        List<SyntheticApplicant> applicants = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String segment = pickSegment(random);
            applicants.add(new SyntheticApplicant(
                    "synth-" + seed + "-" + i,
                    3000 + random.nextInt(12000),
                    200 + random.nextInt(4800),
                    0.1 + random.nextDouble() * 0.85,
                    random.nextInt(4),
                    6 + random.nextInt(90),
                    1 + random.nextInt(5),
                    INCOME_BANDS[random.nextInt(INCOME_BANDS.length)],
                    random.nextDouble() < 0.75,
                    segment,
                    Instant.parse("2024-06-01T12:00:00Z").plusSeconds(i)));
        }
        return applicants;
    }

    private static String pickSegment(Random random) {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (int i = 0; i < SEGMENTS.length; i++) {
            cumulative += SEGMENT_WEIGHTS[i];
            if (roll < cumulative) {
                return SEGMENTS[i];
            }
        }
        return SEGMENTS[SEGMENTS.length - 1];
    }

    public record SyntheticApplicant(
            String customerId,
            double currentLimit,
            double requestedIncrease,
            double utilization,
            int delinquencies12m,
            int tenureMonths,
            int ficoBand,
            String incomeBand,
            boolean incomeVerified,
            String segment,
            Instant asOf) {}
}
