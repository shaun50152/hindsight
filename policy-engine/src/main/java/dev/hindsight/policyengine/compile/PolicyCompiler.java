package dev.hindsight.policyengine.compile;

import dev.hindsight.policyengine.cel.CelExpressionCompiler;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.CompiledRule;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.model.Rule;
import dev.hindsight.policyengine.model.ValidationError;
import dev.hindsight.policyengine.validate.PolicyValidator;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PolicyCompiler {

    private PolicyCompiler() {}

    public static CompileResult compileYaml(String yaml) {
        PolicyYamlParser.ParseResult parsed = PolicyYamlParser.parse(yaml);
        return compile(parsed);
    }

    public static CompileResult compile(PolicyYamlParser.ParseResult parsed) {
        List<ValidationError> errors = PolicyValidator.validate(parsed);
        if (!errors.isEmpty()) {
            return new CompileResult(errors, Optional.empty());
        }
        return compileValidatedPolicy(parsed.policy());
    }

    public static CompileResult compileValidatedPolicy(Policy policy) {
        List<ValidationError> errors = new ArrayList<>();
        List<CompiledRule> compiledRules = new ArrayList<>();
        for (int i = 0; i < policy.rules().size(); i++) {
            Rule rule = policy.rules().get(i);
            String rulePath = "rules[" + i + "]";
            Optional<CelExpressionCompiler.CompiledExpression> whenCompiled =
                    CelExpressionCompiler.compileWhen(rulePath + ".when", rule.when(), errors);
            Optional<CelExpressionCompiler.CompiledExpression> maxCompiled = rule.maxIncrease()
                    .flatMap(expr -> CelExpressionCompiler.compileNumeric(rulePath + ".maxIncrease", expr, errors));
            if (whenCompiled.isEmpty()) {
                continue;
            }
            compiledRules.add(new CompiledRule(
                    rule.id(),
                    rule.outcome(),
                    rule.reason(),
                    whenCompiled.get().program(),
                    maxCompiled.map(CelExpressionCompiler.CompiledExpression::program)));
        }
        if (!errors.isEmpty()) {
            return new CompileResult(errors, Optional.empty());
        }
        return new CompileResult(
                List.of(),
                Optional.of(new CompiledPolicy(
                        policy.policyId(),
                        policy.version(),
                        policy.description(),
                        policy.inputs(),
                        policy.defaultOutcome(),
                        List.copyOf(compiledRules))));
    }
}
