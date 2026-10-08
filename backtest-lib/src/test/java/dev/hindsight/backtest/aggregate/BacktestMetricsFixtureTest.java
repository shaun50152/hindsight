package dev.hindsight.backtest.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.backtest.evaluate.BacktestShardEvaluator;
import dev.hindsight.backtest.source.DecisionRecord;
import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand-built fixture: two decisions, candidate policy flips one APPROVE→REFER.
 * Exposure delta = requestedIncrease 2000 on flip from APPROVE.
 * Utilization bins: baseline [1,1,0,0], flip subset [0,1,0,0] → PSI computed below.
 */
class BacktestMetricsFixtureTest {

    private static final String CANDIDATE_YAML =
            """
            policyId: credit-line-increase
            version: 99
            description: fixture
            inputs: applicant
            defaultOutcome: REFER
            rules:
              - id: R-approve
                when: applicant.ficoBand >= 4 && applicant.utilization < 0.3
                outcome: APPROVE
                reason: RC_STRONG
            """;

    @Test
    void exactFlipMatrixExposureAndPsi() {
        CompiledPolicy candidate = compile(CANDIDATE_YAML);
        DecisionRecord keepApprove = decision("APPROVE", 4, 0.2, 2000.0, "A");
        DecisionRecord flipToRefer = decision("APPROVE", 3, 0.4, 2000.0, "B");

        BacktestPartialAggregate partial =
                BacktestShardEvaluator.evaluateAll(candidate, List.of(keepApprove, flipToRefer), 1);
        var merged = BacktestReportMerger.merge(List.of(partial));

        assertThat(merged.totalDecisions()).isEqualTo(2);
        assertThat(merged.flipCount()).isEqualTo(1);
        assertThat(merged.flipMatrix()[0][2]).isEqualTo(1);
        assertThat(merged.flipMatrix()[0][0]).isEqualTo(1);
        assertThat(merged.exposureDelta()).isEqualTo(2000.0);

        assertThat(partial.utilizationBaselineBins()).containsExactly(1, 1, 0, 0);
        assertThat(partial.utilizationFlipBins()).containsExactly(0, 1, 0, 0);
        double utilizationPsi = PsiCalculator.psi(partial.utilizationBaselineBins(), partial.utilizationFlipBins());
        assertThat(merged.utilizationPsi()).isEqualTo(utilizationPsi);
        assertThat(utilizationPsi).isGreaterThan(0.0);
    }

    private static CompiledPolicy compile(String yaml) {
        return PolicyCompiler.compileValidatedPolicy(PolicyYamlParser.parse(yaml).policy())
                .compiled()
                .orElseThrow();
    }

    private static DecisionRecord decision(
            String recordedOutcome, int fico, double utilization, double requestedIncrease, String segment) {
        DecisionMadePayload payload = new DecisionMadePayload(
                "d-" + segment,
                "req-" + segment,
                "credit-line-increase",
                1,
                "hash",
                recordedOutcome,
                List.of(),
                null,
                List.of(),
                new DecisionMadePayload.ApplicantSnapshotPayload(
                        "cust-" + segment,
                        5000,
                        requestedIncrease,
                        utilization,
                        0,
                        24,
                        fico,
                        "M",
                        true,
                        segment,
                        Instant.EPOCH),
                DecisionMadePayload.CURRENT_SCHEMA_VERSION,
                1L,
                null);
        return new DecisionRecord(payload);
    }
}
