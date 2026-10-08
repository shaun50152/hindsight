package dev.hindsight.policy.service;

import dev.hindsight.policy.lifecycle.InvalidPolicyTransitionException;
import dev.hindsight.policy.lifecycle.PolicyLifecycleStateMachine;
import dev.hindsight.policy.lifecycle.PolicyStatus;
import dev.hindsight.policy.lifecycle.PolicyTransition;
import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.policy.messaging.OutboxWriter;
import dev.hindsight.policy.persistence.PolicyEventRepository;
import dev.hindsight.policy.persistence.PolicyRecord;
import dev.hindsight.policy.persistence.PolicyRepository;
import dev.hindsight.policyengine.compile.CompileResult;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.model.ValidationError;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PolicyService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PolicyRepository policyRepository;
    private final PolicyEventRepository policyEventRepository;
    private final OutboxWriter outboxWriter;

    public PolicyService(
            PolicyRepository policyRepository,
            PolicyEventRepository policyEventRepository,
            OutboxWriter outboxWriter) {
        this.policyRepository = policyRepository;
        this.policyEventRepository = policyEventRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public PolicyRecord createDraft(String policyId, String yaml, String authorId) {
        int version = policyRepository.nextVersion(policyId);
        Policy policy = parseAndAlign(policyId, yaml, version);
        String contentHash = PolicyContentHash.hash(policy);
        Instant now = Instant.now();
        PolicyRecord record = new PolicyRecord(
                policyId,
                version,
                contentHash,
                yaml,
                PolicyStatus.DRAFT,
                authorId,
                Optional.empty(),
                Optional.empty(),
                now,
                Optional.empty());
        policyRepository.insert(record);
        recordEvent(policyId, version, "DRAFT_CREATED", authorId, null);
        emitLifecycle(record, authorId, now);
        return record;
    }

    @Transactional
    public PolicyRecord submit(String policyId, int version, String actorId) {
        PolicyRecord current = load(policyId, version);
        assertStatus(current, PolicyStatus.DRAFT);
        CompileResult compiled = PolicyCompiler.compileYaml(current.yaml());
        if (!compiled.errors().isEmpty()) {
            throw new PolicyValidationException(compiled.errors());
        }
        PolicyStatus next = transition(current.status(), PolicyTransition.SUBMIT);
        applyTransition(current, next, actorId, Optional.empty(), Optional.empty(), "SUBMITTED", null);
        return load(policyId, version);
    }

    @Transactional
    public PolicyRecord approve(String policyId, int version, String approverId) {
        PolicyRecord current = load(policyId, version);
        assertStatus(current, PolicyStatus.IN_REVIEW);
        if (current.authorId().equals(approverId)) {
            throw new PolicyConflictException("Maker cannot approve own policy version");
        }
        PolicyStatus next = transition(current.status(), PolicyTransition.APPROVE);
        applyTransition(
                current,
                next,
                approverId,
                Optional.of(approverId),
                Optional.empty(),
                "APPROVED",
                null);
        return load(policyId, version);
    }

    @Transactional
    public PolicyRecord reject(String policyId, int version, String approverId) {
        PolicyRecord current = load(policyId, version);
        assertStatus(current, PolicyStatus.IN_REVIEW);
        PolicyStatus next = transition(current.status(), PolicyTransition.REJECT);
        applyTransition(
                current,
                next,
                approverId,
                Optional.of(approverId),
                Optional.empty(),
                "REJECTED",
                null);
        return load(policyId, version);
    }

    @Transactional
    public PolicyRecord promote(String policyId, int version, PolicyStatus target, Integer canaryPct, String actorId) {
        PolicyRecord current = load(policyId, version);
        PolicyTransition action = promoteAction(target);
        if (target == PolicyStatus.CANARY && (canaryPct == null || canaryPct < 1 || canaryPct > 100)) {
            throw new PolicyConflictException("canaryPct required (1-100) when promoting to CANARY");
        }
        PolicyStatus next = transition(current.status(), action);
        String details = null;
        if (action == PolicyTransition.PROMOTE_ACTIVE) {
            Optional<PolicyRecord> previousActive = policyRepository.findActive(policyId);
            previousActive.ifPresent(prev -> policyRepository.retireOtherActive(policyId, version));
            if (previousActive.isPresent() && previousActive.get().version() != version) {
                details = JSON.writeValueAsString(
                        JSON.createObjectNode().put("previousActiveVersion", previousActive.get().version()));
            }
        }
        Optional<Integer> pct = target == PolicyStatus.CANARY ? Optional.of(canaryPct) : Optional.empty();
        applyTransition(current, next, actorId, Optional.empty(), pct, eventTypeForPromote(action), details);
        return load(policyId, version);
    }

    @Transactional
    public PolicyRecord rollback(String policyId, int version, String actorId) {
        return rollback(policyId, version, actorId, null);
    }

    @Transactional
    public PolicyRecord rollback(String policyId, int version, String actorId, String reason) {
        PolicyRecord current = load(policyId, version);
        if (current.status() != PolicyStatus.SHADOW
                && current.status() != PolicyStatus.CANARY
                && current.status() != PolicyStatus.ACTIVE) {
            throw new PolicyConflictException("Rollback only allowed from SHADOW, CANARY, or ACTIVE");
        }
        PolicyStatus previousStatus = current.status();
        PolicyStatus next = transition(current.status(), PolicyTransition.ROLLBACK);
        String detailsJson = null;
        if (reason != null && !reason.isBlank()) {
            detailsJson = JSON.writeValueAsString(JSON.createObjectNode()
                    .put("reason", reason)
                    .put("source", "guardrail"));
        }
        applyTransition(current, next, actorId, Optional.empty(), Optional.empty(), "ROLLED_BACK", detailsJson);
        if (previousStatus == PolicyStatus.ACTIVE) {
            restorePreviousActive(policyId, version);
        }
        return load(policyId, version);
    }

    @Transactional
    public PolicyRecord retire(String policyId, int version, String actorId) {
        PolicyRecord current = load(policyId, version);
        PolicyStatus next = transition(current.status(), PolicyTransition.RETIRE);
        applyTransition(current, next, actorId, Optional.empty(), Optional.empty(), "RETIRED", null);
        return load(policyId, version);
    }

    public List<PolicyRecord> listVersions(String policyId) {
        return policyRepository.listByPolicyId(policyId);
    }

    public PolicyRecord getVersion(String policyId, int version) {
        return load(policyId, version);
    }

    private void restorePreviousActive(String policyId, int rolledBackVersion) {
        Optional<String> details = policyEventRepository.findLatestDetailsJson(
                policyId, rolledBackVersion, "PROMOTED_ACTIVE");
        if (details.isEmpty()) {
            return;
        }
        JsonNode node = JSON.readTree(details.get());
        if (!node.has("previousActiveVersion")) {
            return;
        }
        int prevVersion = node.get("previousActiveVersion").intValue();
        PolicyRecord prev = load(policyId, prevVersion);
        if (prev.status() != PolicyStatus.RETIRED) {
            return;
        }
        policyRepository.updateStatus(
                policyId,
                prevVersion,
                PolicyStatus.RETIRED,
                PolicyStatus.ACTIVE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        Instant now = Instant.now();
        PolicyRecord restored = load(policyId, prevVersion);
        recordEvent(policyId, prevVersion, "RESTORED_ACTIVE", "system", null);
        emitLifecycle(restored, "system", now);
    }

    private void applyTransition(
            PolicyRecord current,
            PolicyStatus next,
            String actorId,
            Optional<String> approverId,
            Optional<Integer> canaryPct,
            String eventType,
            String detailsJson) {
        boolean updated = policyRepository.updateStatus(
                current.policyId(),
                current.version(),
                current.status(),
                next,
                approverId,
                canaryPct,
                next == PolicyStatus.APPROVED ? Optional.of(Instant.now()) : Optional.empty());
        if (!updated) {
            throw new PolicyConflictException("Concurrent modification of policy " + current.policyId());
        }
        recordEvent(current.policyId(), current.version(), eventType, actorId, detailsJson);
        PolicyRecord after = load(current.policyId(), current.version());
        emitLifecycle(after, actorId, Instant.now());
    }

    private void emitLifecycle(PolicyRecord record, String actor, Instant occurredAt) {
        outboxWriter.enqueueLifecycleEvent(new PolicyLifecycleEvent(
                record.policyId(),
                record.version(),
                record.contentHash(),
                record.status().name(),
                record.canaryPct().orElse(null),
                actor,
                occurredAt,
                PolicyLifecycleEvent.CURRENT_SCHEMA_VERSION,
                record.yaml()));
    }

    private void recordEvent(String policyId, int version, String eventType, String actorId, String detailsJson) {
        policyEventRepository.insert(policyId, version, eventType, actorId, detailsJson);
    }

    private static PolicyStatus transition(PolicyStatus from, PolicyTransition action) {
        try {
            return PolicyLifecycleStateMachine.transition(from, action);
        } catch (InvalidPolicyTransitionException e) {
            throw new PolicyConflictException(e.getMessage());
        }
    }

    private static PolicyTransition promoteAction(PolicyStatus target) {
        return switch (target) {
            case SHADOW -> PolicyTransition.PROMOTE_SHADOW;
            case CANARY -> PolicyTransition.PROMOTE_CANARY;
            case ACTIVE -> PolicyTransition.PROMOTE_ACTIVE;
            default -> throw new PolicyConflictException("Invalid promote target: " + target);
        };
    }

    private static String eventTypeForPromote(PolicyTransition action) {
        return switch (action) {
            case PROMOTE_SHADOW -> "PROMOTED_SHADOW";
            case PROMOTE_CANARY -> "PROMOTED_CANARY";
            case PROMOTE_ACTIVE -> "PROMOTED_ACTIVE";
            default -> "PROMOTED";
        };
    }

    private static Policy parseAndAlign(String policyId, String yaml, int assignedVersion) {
        PolicyYamlParser.ParseResult parsed = PolicyYamlParser.parse(yaml);
        List<ValidationError> errors = new ArrayList<>(parsed.structureErrors());
        if (!parsed.policy().policyId().equals(policyId)) {
            errors.add(new ValidationError("policyId", "must match path policy id '" + policyId + "'"));
        }
        if (!errors.isEmpty()) {
            throw new PolicyValidationException(errors);
        }
        Policy p = parsed.policy();
        return new Policy(
                policyId,
                assignedVersion,
                p.description(),
                p.inputs(),
                p.defaultOutcome(),
                p.rules());
    }

    private PolicyRecord load(String policyId, int version) {
        return policyRepository
                .find(policyId, version)
                .orElseThrow(() -> new PolicyNotFoundException(policyId, version));
    }

    private static void assertStatus(PolicyRecord record, PolicyStatus expected) {
        if (record.status() != expected) {
            throw new PolicyConflictException("Expected status " + expected + " but was " + record.status());
        }
    }
}
