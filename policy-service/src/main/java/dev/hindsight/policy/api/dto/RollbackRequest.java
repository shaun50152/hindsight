package dev.hindsight.policy.api.dto;

import jakarta.validation.constraints.Size;

public record RollbackRequest(@Size(max = 2000) String reason) {}
