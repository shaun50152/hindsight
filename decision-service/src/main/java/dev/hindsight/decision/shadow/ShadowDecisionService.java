package dev.hindsight.decision.shadow;

import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.DecisionShadowPayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.decision.cache.PolicyCache;
import dev.hindsight.decision.cache.PolicyCache.RoutedPolicy;
import dev.hindsight.decision.messaging.DecisionTopics;
import dev.hindsight.decision.messaging.OutboxWriter;
import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.Decision;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("shadow")
public class ShadowDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ShadowDecisionService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PolicyCache policyCache;
    private final OutboxWriter outboxWriter;
    private final ShadowProcessedRepository shadowProcessedRepository;

    public ShadowDecisionService(
            PolicyCache policyCache, OutboxWriter outboxWriter, ShadowProcessedRepository shadowProcessedRepository) {
        this.policyCache = policyCache;
        this.outboxWriter = outboxWriter;
        this.shadowProcessedRepository = shadowProcessedRepository;
    }

    @Transactional
    public void evaluateAndPublish(EventEnvelope envelope) throws Exception {
        DecisionMadePayload made = JSON.treeToValue(envelope.payload(), DecisionMadePayload.class);
        UUID decisionId = UUID.fromString(made.decisionId());
        if (!shadowProcessedRepository.tryMarkProcessed(decisionId)) {
            log.debug("Skipping duplicate shadow eval decisionId={}", decisionId);
            return;
        }

        RoutedPolicy shadow = policyCache.shadowFor(made.policyId()).orElse(null);
        if (shadow == null) {
            log.debug("No SHADOW version for policyId={}", made.policyId());
            return;
        }

        ApplicantSnapshot snapshot = toSnapshot(made.applicant());
        Decision evaluation = PolicyEvaluator.evaluate(shadow.compiled(), snapshot);
        String shadowOutcome = evaluation.outcome().name();
        boolean flipped = !shadowOutcome.equals(made.outcome());

        DecisionShadowPayload payload = new DecisionShadowPayload(
                DecisionShadowPayload.CURRENT_SCHEMA_VERSION,
                made.decisionId(),
                made.policyId(),
                shadow.version(),
                shadow.contentHash(),
                made.outcome(),
                shadowOutcome,
                evaluation.trace().stream()
                        .map(t -> new DecisionMadePayload.RuleTraceEntry(t.ruleId(), t.whenResult()))
                        .toList(),
                flipped);

        JsonNode payloadNode = JSON.valueToTree(payload);
        String eventId = "decision.shadow:" + made.decisionId() + ":" + shadow.contentHash();
        EventEnvelope out = new EventEnvelope(
                eventId,
                DecisionTopics.DECISION_SHADOW,
                envelope.occurredAt() != null ? envelope.occurredAt() : Instant.now(),
                made.requestId(),
                payloadNode);
        outboxWriter.enqueue(DecisionTopics.DECISION_SHADOW, snapshot.customerId(), out);
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
}
