package dev.hindsight.decision.routing;

import dev.hindsight.common.events.VersionRole;
import dev.hindsight.decision.cache.PolicyCache.RoutedPolicy;

public record RoutingResult(RoutedPolicy policy, VersionRole versionRole) {}
