package dev.hindsight.policyengine.evaluate;

import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.hindsight.common.domain.ReasonCode;
import dev.hindsight.policyengine.cel.ApplicantBindings;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.CompiledRule;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.Outcome;
import dev.hindsight.policyengine.model.RuleTraceEntry;
import dev.hindsight.policyengine.proto.Applicant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure policy evaluator: same inputs always yield the same {@link Decision}. */
public final class PolicyEvaluator {

    private PolicyEvaluator() {}

    public static Decision evaluate(CompiledPolicy policy, ApplicantSnapshot snapshot) {
        Applicant applicant = ApplicantBindings.toProto(snapshot);
        Map<String, Object> activation = Map.of("applicant", applicant);
        List<RuleTraceEntry> trace = new ArrayList<>();
        for (CompiledRule rule : policy.rules()) {
            boolean whenResult = evalBoolean(rule.whenProgram(), activation);
            trace.add(new RuleTraceEntry(rule.id(), whenResult));
            if (whenResult) {
                Optional<BigDecimal> maxIncrease = rule.maxIncreaseProgram()
                        .map(program -> evalNumber(program, activation));
                return new Decision(
                        rule.outcome(),
                        List.of(rule.reason()),
                        maxIncrease,
                        List.copyOf(trace),
                        Optional.of(rule.id()));
            }
        }
        return new Decision(
                policy.defaultOutcome(),
                List.<ReasonCode>of(),
                Optional.empty(),
                List.copyOf(trace),
                Optional.empty());
    }

    private static boolean evalBoolean(CelRuntime.Program program, Map<String, Object> activation) {
        try {
            Object result = program.eval(activation);
            if (result instanceof Boolean bool) {
                return bool;
            }
            throw new IllegalStateException("when expression did not return boolean: " + result);
        } catch (CelEvaluationException e) {
            throw new IllegalStateException("when expression evaluation failed: " + e.getMessage(), e);
        }
    }

    private static BigDecimal evalNumber(CelRuntime.Program program, Map<String, Object> activation) {
        try {
            Object result = program.eval(activation);
            return toBigDecimal(result);
        } catch (CelEvaluationException e) {
            throw new IllegalStateException("maxIncrease expression evaluation failed: " + e.getMessage(), e);
        }
    }

    private static BigDecimal toBigDecimal(Object result) {
        return switch (result) {
            case null -> throw new IllegalStateException("maxIncrease expression returned null");
            case BigDecimal bd -> bd;
            case Double d -> BigDecimal.valueOf(d);
            case Float f -> BigDecimal.valueOf(f.doubleValue());
            case Long l -> BigDecimal.valueOf(l);
            case Integer i -> BigDecimal.valueOf(i);
            case Number number -> BigDecimal.valueOf(number.doubleValue());
            default -> new BigDecimal(result.toString());
        };
    }
}
