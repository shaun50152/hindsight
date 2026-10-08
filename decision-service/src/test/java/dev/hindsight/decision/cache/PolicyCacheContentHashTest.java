package dev.hindsight.decision.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PolicyCacheContentHashTest {

    @Test
    void mismatchedContentHashIsNotLoaded() {
        PolicyCache cache = new PolicyCache();
        String yaml = TestPolicyYaml.minimal("p", 1);
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        cache.apply(new PolicyLifecycleEvent(
                "p",
                1,
                "deadbeef".repeat(8),
                "ACTIVE",
                null,
                "ops",
                null,
                Instant.now(),
                PolicyLifecycleEvent.CURRENT_SCHEMA_VERSION,
                yaml));
        assertThat(cache.routeFor("p").active()).isEmpty();
        assertThat(cache.compiledByContentHash(PolicyContentHash.hash(policy))).isEmpty();
    }
}
