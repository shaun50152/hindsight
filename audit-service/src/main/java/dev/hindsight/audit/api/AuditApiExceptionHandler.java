package dev.hindsight.audit.api;

import dev.hindsight.audit.replay.AuditReplayService.ReplayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuditApiExceptionHandler {

    @ExceptionHandler(ReplayException.class)
    ProblemDetail replayFailed(ReplayException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Replay Failed");
        return problem;
    }
}
