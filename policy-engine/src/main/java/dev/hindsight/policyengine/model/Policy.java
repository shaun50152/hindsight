package dev.hindsight.policyengine.model;

import java.util.List;

public record Policy(
        String policyId,
        int version,
        String description,
        String inputs,
        Outcome defaultOutcome,
        List<Rule> rules) {}
