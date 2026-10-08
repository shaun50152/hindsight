package dev.hindsight.policyengine.cel;

import static dev.cel.common.CelFunctionDecl.newFunctionDeclaration;
import static dev.cel.common.CelOverloadDecl.newGlobalOverload;

import com.google.common.collect.ImmutableList;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.common.types.StructTypeReference;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import dev.hindsight.policyengine.proto.Applicant;

public final class CelEnvironments {

    private static final CelCompiler COMPILER = CelCompilerFactory.standardCelCompilerBuilder()
            .addVar(
                    "applicant",
                    StructTypeReference.create(Applicant.getDescriptor().getFullName()))
            .addMessageTypes(Applicant.getDescriptor())
            .addFunctionDeclarations(
                    newFunctionDeclaration(
                            "min",
                            newGlobalOverload(
                                    "policy_min_double",
                                    SimpleType.DOUBLE,
                                    ImmutableList.of(SimpleType.DOUBLE, SimpleType.DOUBLE)),
                            newGlobalOverload(
                                    "policy_min_int",
                                    SimpleType.INT,
                                    ImmutableList.of(SimpleType.INT, SimpleType.INT))),
                    newFunctionDeclaration(
                            "max",
                            newGlobalOverload(
                                    "policy_max_double",
                                    SimpleType.DOUBLE,
                                    ImmutableList.of(SimpleType.DOUBLE, SimpleType.DOUBLE)),
                            newGlobalOverload(
                                    "policy_max_int",
                                    SimpleType.INT,
                                    ImmutableList.of(SimpleType.INT, SimpleType.INT))))
            .build();

    private static final CelRuntime RUNTIME = CelRuntimeFactory.plannerRuntimeBuilder()
            .addFunctionBindings(
                    CelFunctionBinding.from("policy_min_double", Double.class, Double.class, Math::min),
                    CelFunctionBinding.from("policy_min_int", Long.class, Long.class, Math::min),
                    CelFunctionBinding.from("policy_max_double", Double.class, Double.class, Math::max),
                    CelFunctionBinding.from("policy_max_int", Long.class, Long.class, Math::max))
            .build();

    private CelEnvironments() {}

    public static CelAbstractSyntaxTree compile(String expression) throws CelValidationException {
        return COMPILER.compile(expression).getAst();
    }

    public static boolean isBoolean(CelAbstractSyntaxTree ast) {
        return SimpleType.BOOL.equals(ast.getResultType());
    }

    public static boolean isNumeric(CelAbstractSyntaxTree ast) {
        dev.cel.common.types.CelType type = ast.getResultType();
        return SimpleType.INT.equals(type)
                || SimpleType.DOUBLE.equals(type)
                || SimpleType.UINT.equals(type);
    }

    public static CelRuntime.Program program(CelAbstractSyntaxTree ast) {
        try {
            return RUNTIME.createProgram(ast);
        } catch (CelEvaluationException e) {
            throw new IllegalStateException("Failed to plan CEL program", e);
        }
    }
}
