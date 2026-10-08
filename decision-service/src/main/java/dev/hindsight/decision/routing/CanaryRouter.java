package dev.hindsight.decision.routing;

import dev.hindsight.decision.cache.PolicyCache;
import dev.hindsight.decision.cache.PolicyCache.PolicyRoute;
import dev.hindsight.decision.cache.PolicyCache.RoutedPolicy;
import org.springframework.stereotype.Component;

@Component
public class CanaryRouter {

    private final PolicyCache policyCache;

    public CanaryRouter(PolicyCache policyCache) {
        this.policyCache = policyCache;
    }

    public RoutedPolicy select(String policyId, String customerId) {
        PolicyRoute route = policyCache.routeFor(policyId);
        if (route.active().isEmpty()) {
            throw new dev.hindsight.decision.service.NoActivePolicyException(policyId);
        }
        var active = route.active().get();
        if (route.canary().isEmpty()) {
            return active;
        }
        var canary = route.canary().get();
        int bucket = CustomerBucket.bucket0to99(customerId);
        if (bucket < canary.canaryPct()) {
            return canary;
        }
        return active;
    }
}
