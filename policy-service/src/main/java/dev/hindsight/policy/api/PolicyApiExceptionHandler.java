package dev.hindsight.policy.api;

import dev.hindsight.policy.service.PolicyConflictException;
import dev.hindsight.policy.service.PolicyNotFoundException;
import dev.hindsight.policy.service.PolicyValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PolicyApiExceptionHandler {

    @ExceptionHandler(PolicyValidationException.class)
    ResponseEntity<org.springframework.http.ProblemDetail> handleValidation(PolicyValidationException ex) {
        return ResponseEntity.unprocessableContent().body(ProblemDetailsFactory.validationFailed(ex.errors()));
    }

    @ExceptionHandler(PolicyNotFoundException.class)
    ResponseEntity<org.springframework.http.ProblemDetail> handleNotFound(PolicyNotFoundException ex) {
        return ResponseEntity.status(404).body(ProblemDetailsFactory.notFound(ex.getMessage()));
    }

    @ExceptionHandler(PolicyConflictException.class)
    ResponseEntity<org.springframework.http.ProblemDetail> handleConflict(PolicyConflictException ex) {
        return ResponseEntity.status(409).body(ProblemDetailsFactory.conflict(ex.getMessage()));
    }
}
