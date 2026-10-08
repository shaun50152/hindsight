package dev.hindsight.policyengine.validate;

import dev.hindsight.policyengine.cel.CelExpressionCompiler;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.model.Rule;
import dev.hindsight.policyengine.model.ValidationError;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PolicyValidator {

    private PolicyValidator() {}

    public static List<ValidationError> validate(PolicyYamlParser.ParseResult parsed) {
        List<ValidationError> errors = new ArrayList<>(parsed.structureErrors());
        errors.addAll(validatePolicy(parsed.policy()));
        return errors;
    }

    public static List<ValidationError> validatePolicy(Policy policy) {
        List<ValidationError> errors = new ArrayList<>();
        if (!"applicant".equals(policy.inputs())) {
            errors.add(new ValidationError("inputs", "inputs must be 'applicant'"));
        }
        if (policy.defaultOutcome() == null) {
            errors.add(new ValidationError("defaultOutcome", "unknown or missing defaultOutcome"));
        }
        Map<String, Integer> seenIds = new HashMap<>();
        Map<String, Integer> seenWhen = new HashMap<>();
        boolean priorConstantTrue = false;
        for (int i = 0; i < policy.rules().size(); i++) {
            Rule rule = policy.rules().get(i);
            String rulePath = "rules[" + i + "]";
            if (rule.id() == null || rule.id().isBlank()) {
                errors.add(new ValidationError(rulePath + ".id", "rule id must not be blank"));
            } else if (seenIds.containsKey(rule.id())) {
                errors.add(new ValidationError(rulePath + ".id", "duplicate rule id '" + rule.id() + "'"));
            } else {
                seenIds.put(rule.id(), i);
            }
            if (rule.outcome() == null) {
                errors.add(new ValidationError(rulePath + ".outcome", "unknown or missing outcome"));
            }
            if (rule.reason() == null) {
                errors.add(new ValidationError(rulePath + ".reason", "unknown or missing reason"));
            }
            CelExpressionCompiler.compileWhen(rulePath + ".when", rule.when(), errors);
            rule.maxIncrease()
                    .ifPresent(expr -> CelExpressionCompiler.compileNumeric(rulePath + ".maxIncrease", expr, errors));
            String normalizedWhen = normalizeWhen(rule.when());
            if (isConstantFalse(normalizedWhen)) {
                errors.add(new ValidationError(rulePath, "rule is unreachable: when is always false"));
            }
            if (priorConstantTrue) {
                errors.add(new ValidationError(rulePath, "rule is unreachable: an earlier rule always matches"));
            }
            if (seenWhen.containsKey(normalizedWhen)) {
                errors.add(new ValidationError(
                        rulePath,
                        "rule is unreachable: identical when expression as rules["
                                + seenWhen.get(normalizedWhen)
                                + "]"));
            } else if (!normalizedWhen.isBlank()) {
                seenWhen.put(normalizedWhen, i);
            }
            if (isConstantTrue(normalizedWhen)) {
                priorConstantTrue = true;
            }
        }
        return errors;
    }

    private static String normalizeWhen(String when) {
        return when == null ? "" : when.trim().replaceAll("\\s+", " ");
    }

    private static boolean isConstantFalse(String when) {
        return "false".equalsIgnoreCase(when);
    }

    private static boolean isConstantTrue(String when) {
        return "true".equalsIgnoreCase(when);
    }
}
