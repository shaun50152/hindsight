package dev.hindsight.policy.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDraftRequest(@NotBlank String yaml) {}
