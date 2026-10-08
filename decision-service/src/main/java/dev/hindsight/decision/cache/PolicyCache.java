package dev.hindsight.decision.cache;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.policyengine.compile.CompileResult;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PolicyCache {

    private static final Logger log = LoggerFactory.getLogger(PolicyCache.class);

    private final Map<String, CompiledPolicy> compiledByHash = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, VersionView>> versionsByPolicy = new ConcurrentHashMap<>();

    public record VersionView(String contentHash, String status, Integer canaryPct) {}

    public record RoutedPolicy(
            String policyId, int version, String contentHash, CompiledPolicy compiled, Integer canaryPct) {}

    public record PolicyRoute(Optional<RoutedPolicy> active, Optional<RoutedPolicy> canary) {}

    public void apply(PolicyLifecycleEvent event) {
        CompiledPolicy compiled = compileAndVerify(event);
        if (compiled == null) {
            return;
        }
        compiledByHash.put(event.contentHash(), compiled);
        versionsByPolicy
                .computeIfAbsent(event.policyId(), k -> new ConcurrentHashMap<>())
                .put(event.version(), new VersionView(event.contentHash(), event.status(), event.canaryPct()));
        log.info(
                "Policy cache updated policyId={} version={} status={} hash={}",
                event.policyId(),
                event.version(),
                event.status(),
                event.contentHash());
    }

    public Optional<CompiledPolicy> compiledByContentHash(String contentHash) {
        return Optional.ofNullable(compiledByHash.get(contentHash));
    }

    public PolicyRoute routeFor(String policyId) {
        Map<Integer, VersionView> versions = versionsByPolicy.get(policyId);
        if (versions == null || versions.isEmpty()) {
            return new PolicyRoute(Optional.empty(), Optional.empty());
        }
        Optional<RoutedPolicy> active = Optional.empty();
        Optional<RoutedPolicy> canary = Optional.empty();
        for (var entry : versions.entrySet()) {
            VersionView view = entry.getValue();
            CompiledPolicy compiled = compiledByHash.get(view.contentHash());
            if (compiled == null) {
                continue;
            }
            RoutedPolicy routed = new RoutedPolicy(
                    policyId, entry.getKey(), view.contentHash(), compiled, view.canaryPct());
            if ("ACTIVE".equals(view.status())) {
                active = Optional.of(routed);
            } else if ("CANARY".equals(view.status()) && view.canaryPct() != null) {
                canary = Optional.of(routed);
            }
        }
        return new PolicyRoute(active, canary);
    }

    public Optional<RoutedPolicy> findVersion(String policyId, int version) {
        Map<Integer, VersionView> versions = versionsByPolicy.get(policyId);
        if (versions == null) {
            return Optional.empty();
        }
        VersionView view = versions.get(version);
        if (view == null) {
            return Optional.empty();
        }
        CompiledPolicy compiled = compiledByHash.get(view.contentHash());
        if (compiled == null) {
            return Optional.empty();
        }
        return Optional.of(new RoutedPolicy(
                policyId, version, view.contentHash(), compiled, view.canaryPct()));
    }

    public String activeContentHash(String policyId) {
        return routeFor(policyId).active().map(RoutedPolicy::contentHash).orElse(null);
    }

    private CompiledPolicy compileAndVerify(PolicyLifecycleEvent event) {
        if (event.yaml() == null || event.yaml().isBlank()) {
            log.warn("Skipping lifecycle event without yaml policyId={} version={}", event.policyId(), event.version());
            return null;
        }
        PolicyYamlParser.ParseResult parsed = PolicyYamlParser.parse(event.yaml());
        if (!parsed.structureErrors().isEmpty()) {
            log.error("Invalid policy yaml policyId={} version={}: {}", event.policyId(), event.version(), parsed.structureErrors());
            return null;
        }
        Policy policy = parsed.policy();
        String hash = PolicyContentHash.hash(policy);
        if (!hash.equals(event.contentHash())) {
            log.error(
                    "contentHash mismatch policyId={} version={} expected={} computed={}",
                    event.policyId(),
                    event.version(),
                    event.contentHash(),
                    hash);
            return null;
        }
        CompileResult compiled = PolicyCompiler.compileValidatedPolicy(policy);
        if (!compiled.errors().isEmpty()) {
            log.error("Compile failed policyId={} version={}: {}", event.policyId(), event.version(), compiled.errors());
            return null;
        }
        return compiled.compiled().orElse(null);
    }
}
