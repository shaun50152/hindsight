package dev.hindsight.policyengine.cel;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.runtime.CelRuntime;
import dev.hindsight.policyengine.model.ValidationError;
import java.util.List;
import java.util.Optional;

public final class CelExpressionCompiler {

    private CelExpressionCompiler() {}

    public record CompiledExpression(CelRuntime.Program program, CelAbstractSyntaxTree ast) {}

    public static Optional<CompiledExpression> compileWhen(String path, String expression, List<ValidationError> errors) {
        return compile(path, expression, errors, true);
    }

    public static Optional<CompiledExpression> compileNumeric(String path, String expression, List<ValidationError> errors) {
        return compile(path, expression, errors, false);
    }

    private static Optional<CompiledExpression> compile(
            String path, String expression, List<ValidationError> errors, boolean booleanResult) {
        if (expression == null || expression.isBlank()) {
            errors.add(new ValidationError(path, "expression must not be blank"));
            return Optional.empty();
        }
        try {
            CelAbstractSyntaxTree ast = CelEnvironments.compile(expression);
            if (booleanResult) {
                if (!CelEnvironments.isBoolean(ast)) {
                    errors.add(new ValidationError(path, "expression must be boolean"));
                    return Optional.empty();
                }
            } else if (!CelEnvironments.isNumeric(ast)) {
                errors.add(new ValidationError(path, "expression must be numeric"));
                return Optional.empty();
            }
            for (String fn : CelAllowlist.findDisallowedFunctions(ast)) {
                errors.add(new ValidationError(path, "function '" + fn + "' is not allowed"));
            }
            return Optional.of(new CompiledExpression(CelEnvironments.program(ast), ast));
        } catch (CelValidationException e) {
            errors.add(new ValidationError(path, e.getMessage()));
            return Optional.empty();
        }
    }
}
