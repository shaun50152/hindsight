package dev.hindsight.policyengine.testsupport;

import dev.hindsight.policyengine.model.ApplicantSnapshot;
import java.time.Instant;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Provide;

public class ApplicantSnapshots {

    private static final Instant FIXED_AS_OF = Instant.parse("2025-01-01T00:00:00Z");

    public static ApplicantSnapshot baseline() {
        return new ApplicantSnapshot(
                "cust-1",
                10_000,
                1_000,
                0.4,
                0,
                24,
                3,
                "B2",
                true,
                "A",
                Instant.parse("2025-06-01T12:00:00Z"));
    }

    @Provide
    public static Arbitrary<ApplicantSnapshot> applicantSnapshot() {
        Arbitrary<String> customerId = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12);
        Arbitrary<Double> currentLimit = Arbitraries.doubles().between(1_000, 100_000);
        Arbitrary<Double> requested = Arbitraries.doubles().between(0, 50_000);
        Arbitrary<Double> util = Arbitraries.doubles().between(0, 1.5);
        Arbitrary<Integer> delinq = Arbitraries.integers().between(0, 5);
        Arbitrary<Integer> tenure = Arbitraries.integers().between(0, 120);
        Arbitrary<Integer> fico = Arbitraries.integers().between(1, 5);
        Arbitrary<String> incomeBand = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(4);

        return Combinators.combine(
                        Combinators.combine(customerId, currentLimit, requested, util, delinq, tenure, fico, incomeBand)
                                .as(ApplicantCore::new),
                        Arbitraries.of(true, false),
                        Arbitraries.of("A", "B", "C", "D"))
                .as((core, verified, segment) -> new ApplicantSnapshot(
                        core.customerId(),
                        core.currentLimit(),
                        core.requested(),
                        core.util(),
                        core.delinq(),
                        core.tenure(),
                        core.fico(),
                        core.incomeBand(),
                        verified,
                        segment,
                        FIXED_AS_OF));
    }

    private record ApplicantCore(
            String customerId,
            double currentLimit,
            double requested,
            double util,
            int delinq,
            int tenure,
            int fico,
            String incomeBand) {}
}
