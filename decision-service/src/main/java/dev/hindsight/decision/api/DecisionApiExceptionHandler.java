package dev.hindsight.decision.api;

import dev.hindsight.decision.guardrail.DecisionErrorRecorder;
import dev.hindsight.decision.service.DecisionNotFoundException;
import dev.hindsight.decision.service.NoActivePolicyException;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Profile("!shadow")
public class DecisionApiExceptionHandler {

    private final ObjectProvider<DecisionErrorRecorder> errorRecorder;

    public DecisionApiExceptionHandler(ObjectProvider<DecisionErrorRecorder> errorRecorder) {
        this.errorRecorder = errorRecorder;
    }

    @ExceptionHandler(NoActivePolicyException.class)
    ProblemDetail noActivePolicy(NoActivePolicyException ex) {
        errorRecorder.ifAvailable(r -> r.recordFailClosed(ex));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("No Active Policy");
        problem.setType(URI.create("about:blank#no-active-policy"));
        problem.setProperty("policyId", ex.policyId());
        return problem;
    }

    @ExceptionHandler(DecisionNotFoundException.class)
    ProblemDetail notFound(DecisionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }
}
