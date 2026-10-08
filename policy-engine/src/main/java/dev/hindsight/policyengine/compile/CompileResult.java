package dev.hindsight.policyengine.compile;

import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.ValidationError;
import java.util.List;
import java.util.Optional;

public record CompileResult(List<ValidationError> errors, Optional<CompiledPolicy> compiled) {

    public boolean isSuccess() {
        return errors.isEmpty() && compiled.isPresent();
    }
}
