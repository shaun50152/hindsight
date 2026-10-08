package dev.hindsight.policyengine.model;

import dev.cel.runtime.CelRuntime;
import dev.hindsight.common.domain.ReasonCode;
import java.util.Optional;

public record CompiledRule(
        String id,
        Outcome outcome,
        ReasonCode reason,
        CelRuntime.Program whenProgram,
        Optional<CelRuntime.Program> maxIncreaseProgram) {}
