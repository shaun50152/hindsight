package dev.hindsight.decision.service;

import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.VersionRole;
import dev.hindsight.decision.api.dto.CreateDecisionRequest;
import dev.hindsight.decision.api.dto.DecisionResponse;
import dev.hindsight.decision.api.dto.RuleTraceResponse;
import dev.hindsight.decision.cache.PolicyCache.RoutedPolicy;
import dev.hindsight.decision.messaging.DecisionTopics;
import dev.hindsight.decision.messaging.OutboxWriter;
import dev.hindsight.decision.persistence.DecisionRecord;
import dev.hindsight.decision.persistence.DecisionRepository;
import dev.hindsight.decision.routing.CanaryRouter;
import dev.hindsight.decision.routing.RoutingResult;
import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.RuleTraceEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("!shadow")
public class DecisionWriteService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DecisionRepository decisionRepository;
    private final OutboxWriter outboxWriter;
    private final CanaryRouter canaryRouter;
    private final String defaultPolicyId;

    public DecisionWriteService(
            DecisionRepository decisionRepository,
            OutboxWriter outboxWriter,
            CanaryRouter canaryRouter,
            @Value("${hindsight.decision.default-policy-id}") String defaultPolicyId) {
        this.decisionRepository = decisionRepository;
        this.outboxWriter = outboxWriter;
        this.canaryRouter = canaryRouter;
        this.defaultPolicyId = defaultPolicyId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DecisionResponse create(CreateDecisionRequest request, long latencyMs) {
        String policyId = request.policyId() != null && !request.policyId().isBlank()
                ? request.policyId()
                : defaultPolicyId;
        ApplicantSnapshot snapshot = toSnapshot(request.applicant());
        RoutingResult routing = canaryRouter.select(policyId, snapshot.customerId());
        RoutedPolicy routed = routing.policy();
        VersionRole versionRole = routing.versionRole();
        Decision evaluation = PolicyEvaluator.evaluate(routed.compiled(), snapshot);
        UUID decisionId = UUID.randomUUID();
        Instant now = Instant.now();
        DecisionResponse response = toResponse(decisionId, routed, evaluation);
        DecisionRecord record =
                toRecord(decisionId, request.requestId(), policyId, routed, evaluation, snapshot, now);
        decisionRepository.insert(record);
        enqueueOutbox(request.requestId(), snapshot.customerId(), response, snapshot, now, latencyMs, versionRole);
        return response;
    }

    static DecisionResponse toResponse(
            UUID decisionId, RoutedPolicy routed, Decision evaluation) {
        return new DecisionResponse(
                decisionId,
                routed.policyId(),
                routed.version(),
                routed.contentHash(),
                evaluation.outcome().name(),
                evaluation.reasonCodes().stream().map(Enum::name).toList(),
                evaluation.maxIncrease().orElse(null),
                evaluation.trace().stream()
                        .map(t -> new RuleTraceResponse(t.ruleId(), t.whenResult()))
                        .toList());
    }

    static DecisionResponse toResponse(DecisionRecord record) {
        try {
            List<RuleTraceEntry> trace = JSON.readValue(
                    record.ruleTraceJson(),
                    JSON.getTypeFactory().constructCollectionType(List.class, RuleTraceEntry.class));
            List<String> reasons = JSON.readValue(
                    record.reasonCodesJson(),
                    JSON.getTypeFactory().constructCollectionType(List.class, String.class));
            return new DecisionResponse(
                    record.decisionId(),
                    record.policyId(),
                    record.policyVersion(),
                    record.contentHash(),
                    record.outcome(),
                    reasons,
                    record.maxIncrease(),
                    trace.stream()
                            .map(t -> new RuleTraceResponse(t.ruleId(), t.whenResult()))
                            .toList());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read stored decision", e);
        }
    }

    private void enqueueOutbox(
            String requestId,
            String customerId,
            DecisionResponse response,
            ApplicantSnapshot snapshot,
            Instant occurredAt,
            long latencyMs,
            VersionRole versionRole) {
        DecisionMadePayload payload = toPayload(response, requestId, snapshot, latencyMs, versionRole);
        JsonNode payloadNode = JSON.valueToTree(payload);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                DecisionTopics.DECISION_MADE,
                snapshot.asOf() != null ? snapshot.asOf() : occurredAt,
                requestId,
                payloadNode);
        outboxWriter.enqueue(DecisionTopics.DECISION_MADE, customerId, envelope);
    }

    private static DecisionMadePayload toPayload(
            DecisionResponse response,
            String requestId,
            ApplicantSnapshot snapshot,
            long latencyMs,
            VersionRole versionRole) {
        return new DecisionMadePayload(
                response.decisionId().toString(),
                requestId,
                response.policyId(),
                response.policyVersion(),
                response.contentHash(),
                response.outcome(),
                response.reasonCodes(),
                response.maxIncrease(),
                response.rulesEvaluated().stream()
                        .map(r -> new DecisionMadePayload.RuleTraceEntry(r.ruleId(), r.whenResult()))
                        .toList(),
                new DecisionMadePayload.ApplicantSnapshotPayload(
                        snapshot.customerId(),
                        snapshot.currentLimit(),
                        snapshot.requestedIncrease(),
                        snapshot.utilization(),
                        snapshot.delinquencies12m(),
                        snapshot.tenureMonths(),
                        snapshot.ficoBand(),
                        snapshot.incomeBand(),
                        snapshot.incomeVerified(),
                        snapshot.segment(),
                        snapshot.asOf()),
                DecisionMadePayload.CURRENT_SCHEMA_VERSION,
                latencyMs,
                versionRole);
    }

    private static ApplicantSnapshot toSnapshot(CreateDecisionRequest.ApplicantSnapshotDto dto) {
        return new ApplicantSnapshot(
                dto.customerId(),
                dto.currentLimit(),
                dto.requestedIncrease(),
                dto.utilization(),
                dto.delinquencies12m(),
                dto.tenureMonths(),
                dto.ficoBand(),
                dto.incomeBand(),
                dto.incomeVerified(),
                dto.segment(),
                dto.asOf());
    }

    private static DecisionRecord toRecord(
            UUID decisionId,
            String requestId,
            String policyId,
            RoutedPolicy routed,
            Decision evaluation,
            ApplicantSnapshot snapshot,
            Instant createdAt) {
        try {
            return new DecisionRecord(
                    decisionId,
                    requestId,
                    snapshot.customerId(),
                    policyId,
                    routed.version(),
                    routed.contentHash(),
                    evaluation.outcome().name(),
                    JSON.writeValueAsString(
                            evaluation.reasonCodes().stream().map(Enum::name).toList()),
                    evaluation.maxIncrease().orElse(null),
                    JSON.writeValueAsString(evaluation.trace()),
                    JSON.writeValueAsString(snapshot),
                    createdAt);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist decision", e);
        }
    }
}
