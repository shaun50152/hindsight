package dev.hindsight.policyengine.testsupport;

import dev.hindsight.policyengine.compile.CompileResult;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.model.CompiledPolicy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

public final class ValidCompiledPolicies {

    private static final String SIMPLE_POLICY = """
            policyId: generated
            version: 1
            description: generated policy
            inputs: applicant
            defaultOutcome: REFER
            rules:
              - id: R-approve
                when: applicant.ficoBand >= %d
                outcome: APPROVE
                reason: RC_STRONG
              - id: R-decline
                when: applicant.delinquencies12m >= %d
                outcome: DECLINE
                reason: RC_DELINQ
            """;

    private ValidCompiledPolicies() {}

    public static CompiledPolicy compileSimple(int ficoThreshold, int delinqThreshold) {
        CompileResult result = PolicyCompiler.compileYaml(SIMPLE_POLICY.formatted(ficoThreshold, delinqThreshold));
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to compile generated policy: " + result.errors());
        }
        return result.compiled().orElseThrow();
    }

    public static Arbitrary<CompiledPolicy> compiledPolicy() {
        return Arbitraries.integers()
                .between(1, 5)
                .flatMap(fico -> Arbitraries.integers().between(1, 4).map(delinq -> compileSimple(fico, delinq)));
    }
}
