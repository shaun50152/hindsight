package dev.hindsight.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DecisionMadePayloadTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void deserializesV1JsonWithoutNewFields() throws Exception {
        String v1 =
                """
                {
                  "decisionId": "11111111-1111-1111-1111-111111111111",
                  "requestId": "req-1",
                  "policyId": "p1",
                  "policyVersion": 1,
                  "contentHash": "abc",
                  "outcome": "APPROVE",
                  "reasonCodes": ["RC_STRONG"],
                  "maxIncrease": null,
                  "rulesEvaluated": [{"ruleId": "R1", "whenResult": true}],
                  "applicant": {
                    "customerId": "c1",
                    "currentLimit": 5000,
                    "requestedIncrease": 500,
                    "utilization": 0.3,
                    "delinquencies12m": 0,
                    "tenureMonths": 24,
                    "ficoBand": 4,
                    "incomeBand": "M",
                    "incomeVerified": true,
                    "segment": "A",
                    "asOf": "2024-01-01T00:00:00Z"
                  }
                }
                """;

        DecisionMadePayload payload = JSON.readValue(v1, DecisionMadePayload.class);
        assertThat(payload.schemaVersionOrDefault()).isEqualTo(1);
        assertThat(payload.latencyMs()).isNull();
        assertThat(payload.versionRole()).isNull();
        assertThat(payload.outcome()).isEqualTo("APPROVE");
    }

    @Test
    void roundTripsV2Payload() throws Exception {
        DecisionMadePayload payload =
                new DecisionMadePayload(
                        "11111111-1111-1111-1111-111111111111",
                        "req-1",
                        "p1",
                        2,
                        "hash",
                        "DECLINE",
                        List.of("RC_DELINQ"),
                        BigDecimal.ZERO,
                        List.of(new DecisionMadePayload.RuleTraceEntry("R1", true)),
                        new DecisionMadePayload.ApplicantSnapshotPayload(
                                "c1",
                                5000,
                                500,
                                0.3,
                                0,
                                24,
                                4,
                                "M",
                                true,
                                "B",
                                Instant.parse("2024-01-01T00:00:00Z")),
                        DecisionMadePayload.CURRENT_SCHEMA_VERSION,
                        42L,
                        VersionRole.CANARY);

        String json = JSON.writeValueAsString(payload);
        DecisionMadePayload back = JSON.readValue(json, DecisionMadePayload.class);
        assertThat(back.schemaVersion()).isEqualTo(2);
        assertThat(back.latencyMs()).isEqualTo(42L);
        assertThat(back.versionRole()).isEqualTo(VersionRole.CANARY);
    }
}
