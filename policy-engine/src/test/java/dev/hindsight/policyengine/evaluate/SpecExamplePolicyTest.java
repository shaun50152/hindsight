package dev.hindsight.policyengine.evaluate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.common.domain.ReasonCode;
import dev.hindsight.policyengine.compile.CompileResult;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.Outcome;
import dev.hindsight.policyengine.testsupport.ApplicantSnapshots;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpecExamplePolicyTest {

    private static CompiledPolicy policy;

    @BeforeAll
    static void compileSpecPolicy() throws IOException {
        try (InputStream in =
                SpecExamplePolicyTest.class.getResourceAsStream("/policies/credit-line-increase-v7.yaml")) {
            assertThat(in).isNotNull();
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            CompileResult result = PolicyCompiler.compileYaml(yaml);
            assertThat(result.errors()).isEmpty();
            policy = result.compiled().orElseThrow();
        }
    }

    @Test
    void r1DelinquencyDeclines() {
        ApplicantSnapshot base = ApplicantSnapshots.baseline();
        ApplicantSnapshot applicant = new ApplicantSnapshot(
                base.customerId(),
                base.currentLimit(),
                base.requestedIncrease(),
                base.utilization(),
                2,
                base.tenureMonths(),
                base.ficoBand(),
                base.incomeBand(),
                base.incomeVerified(),
                base.segment(),
                base.asOf());
        Decision decision = PolicyEvaluator.evaluate(policy, applicant);
        assertThat(decision.outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(decision.reasonCodes()).containsExactly(ReasonCode.RC_DELINQ);
        assertThat(decision.decidingRuleId()).contains("R1-delinquency");
    }

    @Test
    void r2HighUtilizationDeclines() {
        ApplicantSnapshot base = ApplicantSnapshots.baseline();
        ApplicantSnapshot applicant = new ApplicantSnapshot(
                base.customerId(),
                base.currentLimit(),
                base.requestedIncrease(),
                0.9,
                0,
                6,
                base.ficoBand(),
                base.incomeBand(),
                base.incomeVerified(),
                base.segment(),
                base.asOf());
        Decision decision = PolicyEvaluator.evaluate(policy, applicant);
        assertThat(decision.outcome()).isEqualTo(Outcome.DECLINE);
        assertThat(decision.reasonCodes()).containsExactly(ReasonCode.RC_UTIL_NEW);
        assertThat(decision.decidingRuleId()).contains("R2-high-util");
    }

    @Test
    void r3StrongApproveWithMaxIncrease() {
        ApplicantSnapshot base = ApplicantSnapshots.baseline();
        ApplicantSnapshot applicant = new ApplicantSnapshot(
                base.customerId(),
                10_000,
                base.requestedIncrease(),
                0.3,
                0,
                base.tenureMonths(),
                4,
                base.incomeBand(),
                true,
                base.segment(),
                base.asOf());
        Decision decision = PolicyEvaluator.evaluate(policy, applicant);
        assertThat(decision.outcome()).isEqualTo(Outcome.APPROVE);
        assertThat(decision.reasonCodes()).containsExactly(ReasonCode.RC_STRONG);
        assertThat(decision.decidingRuleId()).contains("R3-strong");
        assertThat(decision.maxIncrease()).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(new BigDecimal("2500")));
    }

    @Test
    void r4LargeRequestRefers() {
        ApplicantSnapshot base = ApplicantSnapshots.baseline();
        ApplicantSnapshot applicant = new ApplicantSnapshot(
                base.customerId(),
                base.currentLimit(),
                11_000,
                base.utilization(),
                0,
                base.tenureMonths(),
                2,
                base.incomeBand(),
                false,
                base.segment(),
                base.asOf());
        Decision decision = PolicyEvaluator.evaluate(policy, applicant);
        assertThat(decision.outcome()).isEqualTo(Outcome.REFER);
        assertThat(decision.reasonCodes()).containsExactly(ReasonCode.RC_MANUAL);
        assertThat(decision.decidingRuleId()).contains("R4-large-request");
    }

    @Test
    void defaultOutcomeWhenNoRuleMatches() {
        ApplicantSnapshot applicant = new ApplicantSnapshot(
                "cust-default",
                5_000,
                500,
                0.6,
                0,
                18,
                2,
                "B1",
                false,
                "B",
                Instant.parse("2025-06-01T12:00:00Z"));
        Decision decision = PolicyEvaluator.evaluate(policy, applicant);
        assertThat(decision.outcome()).isEqualTo(Outcome.REFER);
        assertThat(decision.reasonCodes()).isEmpty();
        assertThat(decision.decidingRuleId()).isEmpty();
    }
}
