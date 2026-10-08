package dev.hindsight.decision.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateDecisionRequest(
        @NotBlank String requestId,
        String policyId,
        @NotNull @Valid ApplicantSnapshotDto applicant) {

    public record ApplicantSnapshotDto(
            @NotBlank String customerId,
            double currentLimit,
            double requestedIncrease,
            double utilization,
            int delinquencies12m,
            int tenureMonths,
            int ficoBand,
            String incomeBand,
            boolean incomeVerified,
            String segment,
            @NotNull Instant asOf) {}
}
