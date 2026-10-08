package dev.hindsight.policyengine.cel;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.CelCall;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Restricts policy expressions to the spec allowlist beyond operators. */
public final class CelAllowlist {

    private static final Set<String> ALLOWED_FUNCTIONS = Set.of("min", "max", "abs", "size");

    private CelAllowlist() {}

    public static List<String> findDisallowedFunctions(CelAbstractSyntaxTree ast) {
        Set<String> violations = new HashSet<>();
        walk(ast.getExpr(), violations);
        return violations.stream().sorted().toList();
    }

    private static void walk(CelExpr expr, Set<String> violations) {
        if (expr == null) {
            return;
        }
        if (expr.getKind() == CelExpr.ExprKind.Kind.CALL) {
            CelCall call = expr.call();
            String function = call.function();
            if (!function.isEmpty() && !ALLOWED_FUNCTIONS.contains(function) && !isOperator(function)) {
                violations.add(function);
            }
            call.target().ifPresent(target -> walk(target, violations));
            for (CelExpr arg : call.args()) {
                walk(arg, violations);
            }
        }
        if (expr.getKind() == CelExpr.ExprKind.Kind.SELECT) {
            walk(expr.select().operand(), violations);
        }
        if (expr.getKind() == CelExpr.ExprKind.Kind.LIST) {
            for (CelExpr element : expr.list().elements()) {
                walk(element, violations);
            }
        }
        if (expr.getKind() == CelExpr.ExprKind.Kind.STRUCT) {
            for (CelExpr.CelStruct.Entry entry : expr.struct().entries()) {
                walk(entry.value(), violations);
            }
        }
        if (expr.getKind() == CelExpr.ExprKind.Kind.MAP) {
            for (CelExpr.CelMap.Entry entry : expr.map().entries()) {
                walk(entry.key(), violations);
                walk(entry.value(), violations);
            }
        }
        if (expr.getKind() == CelExpr.ExprKind.Kind.COMPREHENSION) {
            CelExpr.CelComprehension comprehension = expr.comprehension();
            walk(comprehension.iterRange(), violations);
            walk(comprehension.accuInit(), violations);
            walk(comprehension.loopCondition(), violations);
            walk(comprehension.loopStep(), violations);
            walk(comprehension.result(), violations);
        }
    }

    private static boolean isOperator(String function) {
        return function.startsWith("_") && function.endsWith("_");
    }
}
