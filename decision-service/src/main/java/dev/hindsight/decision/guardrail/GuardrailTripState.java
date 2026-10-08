package dev.hindsight.decision.guardrail;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!shadow")
public class GuardrailTripState {

    private final Map<String, Instant> cooldownUntilByPolicy = new ConcurrentHashMap<>();
    private final Map<String, String> activeBreachFingerprint = new ConcurrentHashMap<>();

    public boolean inCooldown(String policyId, Instant now) {
        Instant until = cooldownUntilByPolicy.get(policyId);
        return until != null && now.isBefore(until);
    }

    public void enterCooldown(String policyId, Instant now, java.time.Duration cooldown) {
        cooldownUntilByPolicy.put(policyId, now.plus(cooldown));
    }

    public boolean alreadyTripped(String fingerprint) {
        return activeBreachFingerprint.containsKey(fingerprint);
    }

    public void markTripped(String policyId, String fingerprint) {
        activeBreachFingerprint.put(fingerprint, policyId);
    }
}
