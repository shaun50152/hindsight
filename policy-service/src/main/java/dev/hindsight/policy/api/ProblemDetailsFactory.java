package dev.hindsight.policy.api;

import dev.hindsight.policyengine.model.ValidationError;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailsFactory {

    private ProblemDetailsFactory() {}

    public static ProblemDetail validationFailed(List<ValidationError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Policy validation failed");
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("about:blank#policy-validation"));
        problem.setProperty(
                "errors",
                errors.stream()
                        .map(e -> Map.of("path", e.path(), "message", e.message()))
                        .toList());
        return problem;
    }

    public static ProblemDetail conflict(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Conflict");
        return problem;
    }

    public static ProblemDetail notFound(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle("Not Found");
        return problem;
    }
}
