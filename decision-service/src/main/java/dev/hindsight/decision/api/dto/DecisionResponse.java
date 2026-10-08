package dev.hindsight.decision.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DecisionResponse(
        UUID decisionId,
        String policyId,
        int policyVersion,
        String contentHash,
        String outcome,
        List<String> reasonCodes,
        BigDecimal maxIncrease,
        List<RuleTraceResponse> rulesEvaluated) {}
