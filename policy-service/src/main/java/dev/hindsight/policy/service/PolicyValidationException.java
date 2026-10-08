package dev.hindsight.policy.service;

import dev.hindsight.policyengine.model.ValidationError;
import java.util.List;

public final class PolicyValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public PolicyValidationException(List<ValidationError> errors) {
        super("Policy validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
