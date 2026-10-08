package dev.hindsight.policy.api.dto;

import java.util.List;

public record RuleDiffResponse(List<String> added, List<String> removed, List<String> changed, List<String> reordered) {}
