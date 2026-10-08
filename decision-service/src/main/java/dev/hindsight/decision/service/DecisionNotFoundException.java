package dev.hindsight.decision.service;

import java.util.UUID;

public class DecisionNotFoundException extends RuntimeException {

    public DecisionNotFoundException(UUID decisionId) {
        super("Decision not found: " + decisionId);
    }
}
