package dev.hindsight.audit.replay;

import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.audit.persistence.AuditLogRepository;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditReplayService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditLogRepository auditLogRepository;
    private final String chainId;

    public AuditReplayService(
            AuditLogRepository auditLogRepository, @Value("${hindsight.audit.chain-id:main}") String chainId) {
        this.auditLogRepository = auditLogRepository;
        this.chainId = chainId;
    }

    public ReplayResult replay(UUID decisionId, Optional<Integer> policyVersionOverride) {
        DecisionMadePayload decisionPayload = findDecision(decisionId);
        String contentHash = decisionPayload.contentHash();
        if (policyVersionOverride.isPresent()) {
            contentHash = findLifecycleByVersion(decisionPayload.policyId(), policyVersionOverride.get())
                    .map(PolicyLifecycleEvent::contentHash)
                    .orElse(contentHash);
        }
        final String hashForReplay = contentHash;
        PolicyLifecycleEvent policyEvent = findLifecycleByContentHash(hashForReplay)
                .orElseThrow(() -> new ReplayException("Policy version not found in audit log for hash " + hashForReplay));
        CompiledPolicy compiled = compilePolicy(policyEvent);
        ApplicantSnapshot snapshot = toSnapshot(decisionPayload.applicant());
        Decision fresh = PolicyEvaluator.evaluate(compiled, snapshot);
        Decision stored = toDecision(decisionPayload);
        if (sameOutcome(fresh, stored)) {
            return ReplayResult.match(decisionId, policyEvent.version(), contentHash);
        }
        return ReplayResult.mismatch(decisionId, diff(stored, fresh));
    }

    private DecisionMadePayload findDecision(UUID decisionId) {
        return auditLogRepository.findRange(chainId, 1, Long.MAX_VALUE).stream()
                .filter(r -> "decision.made".equals(r.eventType()))
                .map(r -> {
                    try {
                        return JSON.readValue(r.payloadJson(), DecisionMadePayload.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(p -> p != null && decisionId.toString().equals(p.decisionId()))
                .findFirst()
                .orElseThrow(() -> new ReplayException("Decision not found in audit log: " + decisionId));
    }

    private Optional<PolicyLifecycleEvent> findLifecycleByContentHash(String contentHash) {
        return auditLogRepository.findRange(chainId, 1, Long.MAX_VALUE).stream()
                .filter(r -> "policy.lifecycle".equals(r.eventType()))
                .map(r -> readLifecycle(r.payloadJson()))
                .filter(e -> contentHash.equals(e.contentHash()))
                .max(Comparator.comparing(PolicyLifecycleEvent::occurredAt));
    }

    private Optional<PolicyLifecycleEvent> findLifecycleByVersion(String policyId, int version) {
        return auditLogRepository.findRange(chainId, 1, Long.MAX_VALUE).stream()
                .filter(r -> "policy.lifecycle".equals(r.eventType()))
                .map(r -> readLifecycle(r.payloadJson()))
                .filter(e -> policyId.equals(e.policyId()) && e.version() == version)
                .max(Comparator.comparing(PolicyLifecycleEvent::occurredAt));
    }

    private PolicyLifecycleEvent readLifecycle(String json) {
        try {
            return JSON.readValue(json, PolicyLifecycleEvent.class);
        } catch (Exception e) {
            throw new ReplayException("Invalid lifecycle payload", e);
        }
    }

    private CompiledPolicy compilePolicy(PolicyLifecycleEvent event) {
        PolicyYamlParser.ParseResult parsed = PolicyYamlParser.parse(event.yaml());
        Policy policy = parsed.policy();
        if (!PolicyContentHash.hash(policy).equals(event.contentHash())) {
            throw new ReplayException("Policy contentHash mismatch in audit log");
        }
        return PolicyCompiler.compileValidatedPolicy(policy)
                .compiled()
                .orElseThrow(() -> new ReplayException("Failed to compile policy from audit log"));
    }

    private static ApplicantSnapshot toSnapshot(DecisionMadePayload.ApplicantSnapshotPayload a) {
        return new ApplicantSnapshot(
                a.customerId(),
                a.currentLimit(),
                a.requestedIncrease(),
                a.utilization(),
                a.delinquencies12m(),
                a.tenureMonths(),
                a.ficoBand(),
                a.incomeBand(),
                a.incomeVerified(),
                a.segment(),
                a.asOf());
    }

    private static Decision toDecision(DecisionMadePayload p) {
        return new Decision(
                dev.hindsight.policyengine.model.Outcome.valueOf(p.outcome()),
                p.reasonCodes().stream()
                        .map(dev.hindsight.common.domain.ReasonCode::valueOf)
                        .toList(),
                Optional.ofNullable(p.maxIncrease()),
                List.of(),
                Optional.empty());
    }

    private static boolean sameOutcome(Decision a, Decision b) {
        return a.outcome() == b.outcome()
                && a.reasonCodes().equals(b.reasonCodes())
                && a.maxIncrease().equals(b.maxIncrease());
    }

    private static List<String> diff(Decision stored, Decision fresh) {
        List<String> diffs = new ArrayList<>();
        if (stored.outcome() != fresh.outcome()) {
            diffs.add("outcome: stored=" + stored.outcome() + " replay=" + fresh.outcome());
        }
        if (!stored.reasonCodes().equals(fresh.reasonCodes())) {
            diffs.add("reasonCodes: stored=" + stored.reasonCodes() + " replay=" + fresh.reasonCodes());
        }
        if (!stored.maxIncrease().equals(fresh.maxIncrease())) {
            diffs.add("maxIncrease: stored=" + stored.maxIncrease() + " replay=" + fresh.maxIncrease());
        }
        return diffs;
    }

    public record ReplayResult(String status, UUID decisionId, Integer policyVersion, String contentHash, List<String> diffs) {

        static ReplayResult match(UUID decisionId, int version, String hash) {
            return new ReplayResult("MATCH", decisionId, version, hash, List.of());
        }

        static ReplayResult mismatch(UUID decisionId, List<String> diffs) {
            return new ReplayResult("MISMATCH", decisionId, null, null, diffs);
        }
    }

    public static class ReplayException extends RuntimeException {
        public ReplayException(String message) {
            super(message);
        }

        public ReplayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
