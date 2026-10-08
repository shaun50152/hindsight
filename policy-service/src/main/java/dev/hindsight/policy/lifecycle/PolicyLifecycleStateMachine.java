package dev.hindsight.policy.lifecycle;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PolicyLifecycleStateMachine {

    private static final Map<TransitionKey, PolicyStatus> TRANSITIONS = buildTransitions();

    private PolicyLifecycleStateMachine() {}

    public static PolicyStatus transition(PolicyStatus from, PolicyTransition action) {
        PolicyStatus to = TRANSITIONS.get(new TransitionKey(from, action));
        if (to == null) {
            throw new InvalidPolicyTransitionException(from, action);
        }
        return to;
    }

    public static Set<PolicyTransition> allowedTransitions(PolicyStatus from) {
        EnumSet<PolicyTransition> allowed = EnumSet.noneOf(PolicyTransition.class);
        for (Map.Entry<TransitionKey, PolicyStatus> entry : TRANSITIONS.entrySet()) {
            if (entry.getKey().from() == from) {
                allowed.add(entry.getKey().action());
            }
        }
        return Set.copyOf(allowed);
    }

    private static Map<TransitionKey, PolicyStatus> buildTransitions() {
        Map<TransitionKey, PolicyStatus> map = new HashMap<>();
        map.put(new TransitionKey(PolicyStatus.DRAFT, PolicyTransition.SUBMIT), PolicyStatus.IN_REVIEW);
        map.put(new TransitionKey(PolicyStatus.IN_REVIEW, PolicyTransition.APPROVE), PolicyStatus.APPROVED);
        map.put(new TransitionKey(PolicyStatus.IN_REVIEW, PolicyTransition.REJECT), PolicyStatus.REJECTED);
        map.put(new TransitionKey(PolicyStatus.APPROVED, PolicyTransition.PROMOTE_SHADOW), PolicyStatus.SHADOW);
        map.put(new TransitionKey(PolicyStatus.SHADOW, PolicyTransition.PROMOTE_CANARY), PolicyStatus.CANARY);
        map.put(new TransitionKey(PolicyStatus.CANARY, PolicyTransition.PROMOTE_ACTIVE), PolicyStatus.ACTIVE);
        map.put(new TransitionKey(PolicyStatus.ACTIVE, PolicyTransition.RETIRE), PolicyStatus.RETIRED);
        map.put(new TransitionKey(PolicyStatus.SHADOW, PolicyTransition.ROLLBACK), PolicyStatus.RETIRED);
        map.put(new TransitionKey(PolicyStatus.CANARY, PolicyTransition.ROLLBACK), PolicyStatus.RETIRED);
        map.put(new TransitionKey(PolicyStatus.ACTIVE, PolicyTransition.ROLLBACK), PolicyStatus.RETIRED);
        return Map.copyOf(map);
    }

    private record TransitionKey(PolicyStatus from, PolicyTransition action) {}
}
