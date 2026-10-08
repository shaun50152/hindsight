package dev.hindsight.backtest.evaluate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.backtest.aggregate.BacktestPartialAggregate;
import dev.hindsight.backtest.aggregate.BacktestPartialJson;
import dev.hindsight.backtest.aggregate.BacktestReportMerger;
import dev.hindsight.backtest.source.DecisionRecord;
import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestShardEvaluatorTest {

    private static final String POLICY_YAML =
            """
            policyId: credit-line-increase
            version: 1
            description: test
            inputs: applicant
            defaultOutcome: REFER
            rules:
              - id: R-decline
                when: applicant.delinquencies12m >= 2
                outcome: DECLINE
                reason: RC_DELINQ
              - id: R-approve
                when: applicant.ficoBand >= 3 && applicant.utilization < 0.5
                outcome: APPROVE
                reason: RC_STRONG
            """;

    @Test
    void sameOutcomeYieldsZeroFlips() {
        CompiledPolicy policy = compile(POLICY_YAML);
        DecisionRecord record = record("APPROVE", 3, 0.4, 0, "A");
        BacktestPartialAggregate partial = BacktestShardEvaluator.evaluateAll(policy, List.of(record), 1);
        assertThat(partial.flipCount()).isZero();
        assertThat(partial.flipMatrix()[0][0]).isEqualTo(1);
    }

    @Test
    void partialJsonRoundTrip() {
        CompiledPolicy policy = compile(POLICY_YAML);
        DecisionRecord approve = record("APPROVE", 3, 0.4, 0, "A");
        DecisionRecord decline = record("DECLINE", 1, 0.9, 2, "B");
        BacktestPartialAggregate partial =
                BacktestShardEvaluator.evaluateAll(policy, List.of(approve, decline), 1);
        BacktestPartialAggregate back = BacktestPartialJson.fromJson(BacktestPartialJson.toJson(partial));
        assertThat(back.flipCount()).isEqualTo(partial.flipCount());
        assertThat(back.exposureDelta()).isEqualTo(partial.exposureDelta());
    }

    @Test
    void mergeComputesExposureAndMatrix() {
        CompiledPolicy policy = compile(POLICY_YAML);
        DecisionRecord r1 = record("APPROVE", 3, 0.4, 0, "A");
        DecisionRecord r2 = record("DECLINE", 1, 0.9, 2, "B");
        BacktestPartialAggregate p1 = BacktestShardEvaluator.evaluateAll(policy, List.of(r1), 1);
        BacktestPartialAggregate p2 = BacktestShardEvaluator.evaluateAll(policy, List.of(r2), 1);
        var merged = BacktestReportMerger.merge(List.of(p1, p2));
        assertThat(merged.totalDecisions()).isEqualTo(2);
        assertThat(merged.flipMatrix()).isNotNull();
    }

    private static CompiledPolicy compile(String yaml) {
        var parsed = PolicyYamlParser.parse(yaml);
        return PolicyCompiler.compileValidatedPolicy(parsed.policy())
                .compiled()
                .orElseThrow();
    }

    private static DecisionRecord record(
            String outcome, int fico, double utilization, int delinq, String segment) {
        DecisionMadePayload payload = new DecisionMadePayload(
                "d1",
                "req1",
                "credit-line-increase",
                1,
                "hash",
                outcome,
                List.of(),
                null,
                List.of(),
                new DecisionMadePayload.ApplicantSnapshotPayload(
                        "c1", 5000, 1000, utilization, delinq, 24, fico, "M", true, segment, Instant.EPOCH),
                DecisionMadePayload.CURRENT_SCHEMA_VERSION,
                1L,
                null);
        return new DecisionRecord(payload);
    }
}
