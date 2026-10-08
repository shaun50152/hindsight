package dev.hindsight.policyengine.validate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.policyengine.compile.CompileResult;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.model.ValidationError;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyValidatorTest {

    @Test
    void rejectsUnknownTopLevelField() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                mystery: true
                rules: []
                """);
        assertThat(errors).anyMatch(e -> e.path().equals("mystery"));
    }

    @Test
    void rejectsUnknownRuleField() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "true"
                    outcome: REFER
                    reason: RC_MANUAL
                    extra: 1
                """);
        assertThat(errors).anyMatch(e -> e.path().contains("extra"));
    }

    @Test
    void rejectsWrongInputs() {
        List<ValidationError> errors = validate(minimalPolicy("wrong"));
        assertThat(errors).anyMatch(e -> e.path().equals("inputs"));
    }

    @Test
    void rejectsDuplicateRuleIds() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "false"
                    outcome: REFER
                    reason: RC_MANUAL
                  - id: R1
                    when: "false"
                    outcome: REFER
                    reason: RC_MANUAL
                """);
        assertThat(errors).anyMatch(e -> e.message().contains("duplicate rule id"));
    }

    @Test
    void rejectsUnknownOutcome() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "true"
                    outcome: MAYBE
                    reason: RC_MANUAL
                """);
        assertThat(errors).anyMatch(e -> e.path().endsWith("outcome"));
    }

    @Test
    void rejectsUnknownReason() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "true"
                    outcome: REFER
                    reason: RC_UNKNOWN
                """);
        assertThat(errors).anyMatch(e -> e.path().endsWith("reason"));
    }

    @Test
    void rejectsNonBooleanWhen() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: applicant.currentLimit
                    outcome: REFER
                    reason: RC_MANUAL
                """);
        assertThat(errors).anyMatch(e -> e.path().endsWith("when") && e.message().contains("boolean"));
    }

    @Test
    void rejectsInvalidCelSyntax() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: applicant. &&
                    outcome: REFER
                    reason: RC_MANUAL
                """);
        assertThat(errors).anyMatch(e -> e.path().endsWith("when"));
    }

    @Test
    void rejectsDisallowedFunction() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: sqrt(applicant.currentLimit) > 0
                    outcome: REFER
                    reason: RC_MANUAL
                """);
        assertThat(errors).anyMatch(e -> e.message().contains("sqrt"));
    }

    @Test
    void rejectsNonNumericMaxIncrease() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "true"
                    outcome: APPROVE
                    reason: RC_STRONG
                    maxIncrease: applicant.incomeVerified
                """);
        assertThat(errors).anyMatch(e -> e.path().endsWith("maxIncrease"));
    }

    @Test
    void flagsUnreachableConstantFalseRule() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "false"
                    outcome: REFER
                    reason: RC_MANUAL
                """);
        assertThat(errors).anyMatch(e -> e.message().contains("unreachable") && e.message().contains("false"));
    }

    @Test
    void flagsUnreachableDuplicateWhen() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: applicant.ficoBand >= 1
                    outcome: REFER
                    reason: RC_MANUAL
                  - id: R2
                    when: applicant.ficoBand >= 1
                    outcome: DECLINE
                    reason: RC_DELINQ
                """);
        assertThat(errors).anyMatch(e -> e.message().contains("identical when"));
    }

    @Test
    void flagsUnreachableRulesAfterAlwaysTrue() {
        List<ValidationError> errors = validate("""
                policyId: p
                version: 1
                description: d
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "true"
                    outcome: REFER
                    reason: RC_MANUAL
                  - id: R2
                    when: applicant.ficoBand >= 1
                    outcome: DECLINE
                    reason: RC_DELINQ
                """);
        assertThat(errors).anyMatch(e -> e.message().contains("earlier rule always matches"));
    }

    @Test
    void compileSucceedsForValidPolicy() {
        CompileResult result = PolicyCompiler.compileYaml(minimalPolicy("applicant"));
        assertThat(result.isSuccess()).isTrue();
    }

    private static List<ValidationError> validate(String yaml) {
        PolicyYamlParser.ParseResult parsed = PolicyYamlParser.parse(yaml);
        return PolicyValidator.validate(parsed);
    }

    private static String minimalPolicy(String inputs) {
        return """
                policyId: p
                version: 1
                description: d
                inputs: %s
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "true"
                    outcome: REFER
                    reason: RC_MANUAL
                """
                .formatted(inputs);
    }
}
