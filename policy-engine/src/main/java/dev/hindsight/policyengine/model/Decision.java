package dev.hindsight.policyengine.model;

import dev.hindsight.common.domain.ReasonCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record Decision(
        Outcome outcome,
        List<ReasonCode> reasonCodes,
        Optional<BigDecimal> maxIncrease,
        List<RuleTraceEntry> trace,
        Optional<String> decidingRuleId) {}
