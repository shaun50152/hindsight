package dev.hindsight.audit.messaging;

import dev.hindsight.audit.chain.AuditAppender;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.PolicyLifecycleEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuditIngestionListener {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditAppender auditAppender;

    public AuditIngestionListener(AuditAppender auditAppender) {
        this.auditAppender = auditAppender;
    }

    @KafkaListener(topics = {AuditTopics.DECISION_MADE, AuditTopics.POLICY_LIFECYCLE}, concurrency = "1")
    void onMessage(String raw) throws Exception {
        JsonNode root = JSON.readTree(raw);
        if (root.has("eventId") && root.has("type")) {
            EventEnvelope envelope = JSON.treeToValue(root, EventEnvelope.class);
            auditAppender.append(envelope.eventId(), envelope.type(), envelope.payload());
        } else {
            PolicyLifecycleEvent event = JSON.treeToValue(root, PolicyLifecycleEvent.class);
            String eventId = "policy.lifecycle:"
                    + PolicyLifecycleEvent.messageKey(event.policyId(), event.version())
                    + ":"
                    + event.status()
                    + ":"
                    + event.contentHash();
            JsonNode payload = JSON.valueToTree(event);
            auditAppender.append(eventId, AuditTopics.POLICY_LIFECYCLE, payload);
        }
    }
}
