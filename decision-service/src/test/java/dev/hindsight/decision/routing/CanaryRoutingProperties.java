package dev.hindsight.decision.routing;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.decision.cache.PolicyCache;
import dev.hindsight.decision.cache.PolicyCache.RoutedPolicy;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class CanaryRoutingProperties {

    @Property
    void sameCustomerAndPctYieldsSameVersion(
            @ForAll @IntRange(min = 1, max = 100_000) int customerNum, @ForAll @IntRange(min = 1, max = 100) int pct) {
        String customerId = "cust-" + customerNum;
        PolicyCache cache = cacheWithCanary(pct);
        CanaryRouter router = new CanaryRouter(cache);
        var first = router.select("p", customerId);
        var second = router.select("p", customerId);
        assertSameVersion(first, second);
    }

    @Property
    void raisingPctOnlyMovesControlToCanary(@ForAll @IntRange(min = 1, max = 100_000) int customerNum) {
        String customerId = "cust-" + customerNum;
        PolicyCache low = cacheWithCanary(10);
        PolicyCache high = cacheWithCanary(50);
        CanaryRouter lowRouter = new CanaryRouter(low);
        CanaryRouter highRouter = new CanaryRouter(high);
        var atLow = lowRouter.select("p", customerId);
        var atHigh = highRouter.select("p", customerId);
        if (atLow.policy().version() == 2) {
            assertSameVersion(atLow, atHigh);
        }
    }

    private static void assertSameVersion(RoutingResult a, RoutingResult b) {
        org.assertj.core.api.Assertions.assertThat(a.policy().version()).isEqualTo(b.policy().version());
        org.assertj.core.api.Assertions.assertThat(a.policy().contentHash()).isEqualTo(b.policy().contentHash());
    }

    private static PolicyCache cacheWithCanary(int pct) {
        PolicyCache cache = new PolicyCache();
        applyActive(cache, 1);
        applyCanary(cache, 2, pct);
        return cache;
    }

    private static void applyActive(PolicyCache cache, int version) {
        String yaml = TestPolicyYaml.minimal("p", version);
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        cache.apply(new PolicyLifecycleEvent(
                "p",
                version,
                PolicyContentHash.hash(policy),
                "ACTIVE",
                null,
                "ops",
                Instant.now(),
                1,
                yaml));
    }

    private static void applyCanary(PolicyCache cache, int version, int pct) {
        String yaml = TestPolicyYaml.canaryMarker("p", version);
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        cache.apply(new PolicyLifecycleEvent(
                "p",
                version,
                PolicyContentHash.hash(policy),
                "CANARY",
                pct,
                "ops",
                Instant.now(),
                1,
                yaml));
    }
}
