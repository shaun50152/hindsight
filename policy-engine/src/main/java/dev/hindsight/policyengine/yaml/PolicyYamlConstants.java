package dev.hindsight.policyengine.yaml;

import java.util.Set;

final class PolicyYamlConstants {

    static final Set<String> TOP_LEVEL_KEYS =
            Set.of("policyId", "version", "description", "inputs", "defaultOutcome", "rules");

    static final Set<String> RULE_KEYS = Set.of("id", "when", "outcome", "reason", "maxIncrease");

    private PolicyYamlConstants() {}
}
