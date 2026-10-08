package dev.hindsight.policyengine.model;

import java.util.List;

public record CompiledPolicy(
        String policyId,
        int version,
        String description,
        String inputs,
        Outcome defaultOutcome,
        List<CompiledRule> rules) {}
