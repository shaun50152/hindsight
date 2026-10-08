package dev.hindsight.policyengine.cel;

import com.google.protobuf.Timestamp;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.proto.Applicant;
import java.time.Instant;

public final class ApplicantBindings {

    private ApplicantBindings() {}

    public static Applicant toProto(ApplicantSnapshot snapshot) {
        Applicant.Builder builder = Applicant.newBuilder()
                .setCustomerId(snapshot.customerId())
                .setCurrentLimit(snapshot.currentLimit())
                .setRequestedIncrease(snapshot.requestedIncrease())
                .setUtilization(snapshot.utilization())
                .setDelinquencies12M(snapshot.delinquencies12m())
                .setTenureMonths(snapshot.tenureMonths())
                .setFicoBand(snapshot.ficoBand())
                .setIncomeBand(snapshot.incomeBand())
                .setIncomeVerified(snapshot.incomeVerified())
                .setSegment(snapshot.segment());
        if (snapshot.asOf() != null) {
            builder.setAsOf(toTimestamp(snapshot.asOf()));
        }
        return builder.build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
