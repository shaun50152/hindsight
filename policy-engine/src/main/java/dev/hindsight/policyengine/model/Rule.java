package dev.hindsight.policyengine.model;

import dev.hindsight.common.domain.ReasonCode;
import java.util.Optional;

public record Rule(
        String id,
        String when,
        Outcome outcome,
        ReasonCode reason,
        Optional<String> maxIncrease) {}
