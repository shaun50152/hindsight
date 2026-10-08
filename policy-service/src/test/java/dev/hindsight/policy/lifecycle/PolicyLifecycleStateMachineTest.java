package dev.hindsight.policy.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyLifecycleStateMachineTest {

    private static final Map<PolicyStatus, Map<PolicyTransition, PolicyStatus>> EXPECTED = Map.ofEntries(
            Map.entry(PolicyStatus.DRAFT, Map.of(PolicyTransition.SUBMIT, PolicyStatus.IN_REVIEW)),
            Map.entry(
                    PolicyStatus.IN_REVIEW,
                    Map.of(
                            PolicyTransition.APPROVE, PolicyStatus.APPROVED,
                            PolicyTransition.REJECT, PolicyStatus.REJECTED)),
            Map.entry(PolicyStatus.APPROVED, Map.of(PolicyTransition.PROMOTE_SHADOW, PolicyStatus.SHADOW)),
            Map.entry(PolicyStatus.SHADOW, Map.of(
                    PolicyTransition.PROMOTE_CANARY, PolicyStatus.CANARY,
                    PolicyTransition.ROLLBACK, PolicyStatus.RETIRED)),
            Map.entry(PolicyStatus.CANARY, Map.of(
                    PolicyTransition.PROMOTE_ACTIVE, PolicyStatus.ACTIVE,
                    PolicyTransition.ROLLBACK, PolicyStatus.RETIRED)),
            Map.entry(PolicyStatus.ACTIVE, Map.of(
                    PolicyTransition.RETIRE, PolicyStatus.RETIRED,
                    PolicyTransition.ROLLBACK, PolicyStatus.RETIRED)));

    @Test
    void exhaustiveTransitionMatrix() {
        for (PolicyStatus from : PolicyStatus.values()) {
            for (PolicyTransition action : PolicyTransition.values()) {
                Map<PolicyTransition, PolicyStatus> allowed = EXPECTED.getOrDefault(from, Map.of());
                if (allowed.containsKey(action)) {
                    assertThat(PolicyLifecycleStateMachine.transition(from, action))
                            .isEqualTo(allowed.get(action));
                } else {
                    assertThatThrownBy(() -> PolicyLifecycleStateMachine.transition(from, action))
                            .isInstanceOf(InvalidPolicyTransitionException.class);
                }
            }
        }
    }
}
