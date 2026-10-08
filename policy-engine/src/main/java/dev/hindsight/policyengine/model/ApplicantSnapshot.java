package dev.hindsight.policyengine.model;

import java.time.Instant;

/** Synthetic applicant input snapshot (spec §3). */
public record ApplicantSnapshot(
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
