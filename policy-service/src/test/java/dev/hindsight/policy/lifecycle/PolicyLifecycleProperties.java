package dev.hindsight.policy.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class PolicyLifecycleProperties {

    @Property
    void randomAllowedWalksNeverThrow(@ForAll @IntRange(min = 1, max = 50) int steps, @ForAll long seed) {
        Random random = new Random(seed);
        PolicyStatus status = PolicyStatus.DRAFT;
        List<PolicyTransition> history = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            var allowed = PolicyLifecycleStateMachine.allowedTransitions(status);
            if (allowed.isEmpty()) {
                break;
            }
            var actions = new ArrayList<>(allowed);
            PolicyTransition action = actions.get(random.nextInt(actions.size()));
            status = PolicyLifecycleStateMachine.transition(status, action);
            history.add(action);
        }
        org.assertj.core.api.Assertions.assertThat(status).isNotNull();
    }
}
