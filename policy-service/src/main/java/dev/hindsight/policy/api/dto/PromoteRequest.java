package dev.hindsight.policy.api.dto;

import dev.hindsight.policy.lifecycle.PolicyStatus;
import jakarta.validation.constraints.NotNull;

public record PromoteRequest(@NotNull PolicyStatus target, Integer canaryPct) {}
